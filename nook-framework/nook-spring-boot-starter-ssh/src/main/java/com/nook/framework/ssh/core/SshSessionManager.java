package com.nook.framework.ssh.core;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 远程 SSH 会话注册表 (lazy 创建, 死链被动失效); 不查 DB / 不调业务 API, 凭据由调用方传入.
 * 同一 server 按 {@link SshSessionScope} 隔离 cache, 避免长任务跟短任务互相 invalidate.
 *
 * @author nook
 */
public interface SshSessionManager {

    /**
     * 拿一个就绪 SSH 会话, 不存在或已死则现场建; 失败立即抛异常不重试.
     * cache key = (cred.serverId, scope), 不同 scope 互不影响.
     *
     * @param cred  SSH 凭据 (caller 已从业务侧解析)
     * @param scope 会话作用域
     * @return SshSession
     */
    SshSession acquire(SessionCredential cred, SshSessionScope scope);

    /**
     * 主动失效指定 scope 的 cache session, 下次 acquire(scope) 用最新凭据重建; 其他 scope 不动.
     *
     * @param serverId resource_server.id
     * @param scope    要失效的作用域
     */
    void invalidate(String serverId, SshSessionScope scope);

    /**
     * 失效该 server 所有 scope 的 cache session; 仅在凭据变更等"全量作废"场景用.
     *
     * @param serverId resource_server.id
     */
    void invalidateAll(String serverId);

    /**
     * 全量 session 状态快照, 给运维查询接口用; key 形如 "serverId:scope".
     *
     * @return Map (cacheKey → Snapshot)
     */
    Map<String, SshSession.Snapshot> snapshot();

    /**
     * 一次性会话: 临时 cred (不入业务持久层, 如 IP 池落地节点) 跑完即关, 不进 cache.
     *
     * @param cred   一次性凭据
     * @param action 业务回调
     * @param <T>    返回类型
     * @return action 的返回值
     */
    <T> T runAdHoc(SessionCredential cred, Function<SshSession, T> action);

    /**
     * runAdHoc 的 void 版本.
     *
     * @param cred   一次性凭据
     * @param action 业务回调
     */
    default void runAdHocVoid(SessionCredential cred, Consumer<SshSession> action) {
        runAdHoc(cred, s -> {
            action.accept(s);
            return null;
        });
    }
}
