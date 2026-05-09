package com.nook.biz.node.framework.server.session;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** 远程会话注册表 + 生命周期管理 (lazy 创建, 快速失败, 周期健康检查与 idle-cleanup). */
public interface ServerSessionManager {

    /** Lazy 拿一个就绪会话; 不存在则现场建; 失败立即抛 BACKEND_UNREACHABLE 不重试. */
    ServerSession acquire(String serverId);

    /** 主动失效缓存的 session, 下次 acquire 用最新凭据重建. */
    void invalidate(String serverId);

    /** 全量 session 状态快照, 给运维查询接口用. */
    Map<String, ServerSession.Snapshot> snapshot();

    /** 一次性会话: 临时 cred (不入 resource_server 表, 如 IP 池落地节点) 跑完即关, 不进 cache. */
    <T> T runAdHoc(ServerCredentialDTO cred, Function<ServerSession, T> action);

    /** runAdHoc 的 void 版本. */
    default void runAdHocVoid(ServerCredentialDTO cred, Consumer<ServerSession> action) {
        runAdHoc(cred, s -> {
            action.accept(s);
            return null;
        });
    }
}
