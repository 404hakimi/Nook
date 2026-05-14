package com.nook.framework.ssh.core;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * SSH 会话静态 facade: 业务侧调用入口, 内部委托 framework {@link SshSessionManager} + 业务侧 {@link SshCredentialApi}.
 *
 * <p>启动期由 {@link com.nook.framework.ssh.internal.SshAutoConfiguration} 通过 {@link #init} 注入依赖.
 * 业务模块禁止直接持有 SshSessionManager / SshCredentialApi, 一律走本 facade.
 *
 * <p>设计选择: 静态 API (无需注入), 跟 yudao 风的 DictFrameworkUtils 同款.
 *
 * @author nook
 */
@Slf4j
public class SshSessions {

    private static SshSessionManager sessionManager;
    private static SshCredentialApi credentialApi;

    /** AutoConfig 启动时调一次, 把 framework + 业务 SPI 注入静态字段; 重复 init 仅打 info 日志, 不抛错. */
    public static void init(SshSessionManager sessionManager, SshCredentialApi credentialApi) {
        SshSessions.sessionManager = sessionManager;
        SshSessions.credentialApi = credentialApi;
        log.info("[SshSessions] 初始化完成 (credentialApi={})",
                credentialApi != null ? credentialApi.getClass().getSimpleName() : "null");
    }

    /** 按 serverId 拿就绪 SSH 会话; SPI 加载凭据 → manager 申请. */
    public static SshSession acquire(String serverId, SshSessionScope scope) {
        ensureReady();
        if (credentialApi == null) {
            throw new IllegalStateException(
                    "SshCredentialApi 未配置, 无法按 serverId 拿 session; 业务模块需声明一个实现 bean");
        }
        SessionCredential cred = credentialApi.load(serverId);
        return sessionManager.acquire(cred, scope);
    }

    /** 失效该 server 全部 scope 的缓存 session; 凭据变更事件触发, 下次 acquire 用新凭据重建. */
    public static void invalidate(String serverId) {
        ensureReady();
        sessionManager.invalidateAll(serverId);
    }

    /** 失效指定 scope 的缓存 session, 其他 scope 不动. */
    public static void invalidate(String serverId, SshSessionScope scope) {
        ensureReady();
        sessionManager.invalidate(serverId, scope);
    }

    /** 跑一次性凭据 SSH (前端表单 / 未入业务库的临时节点); 不入 cache, 跑完即关. */
    public static <T> T runAdHoc(SessionCredential cred, Function<SshSession, T> action) {
        ensureReady();
        return sessionManager.runAdHoc(cred, action);
    }

    /** {@link #runAdHoc} 的 void 版本. */
    public static void runAdHocVoid(SessionCredential cred, Consumer<SshSession> action) {
        ensureReady();
        sessionManager.runAdHocVoid(cred, action);
    }

    private static void ensureReady() {
        if (sessionManager == null) {
            throw new IllegalStateException(
                    "SshSessions 未初始化; 检查 SshAutoConfiguration 是否被 Spring Boot 加载");
        }
    }
}
