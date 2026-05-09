package com.nook.biz.node.framework.ssh;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 远程 SSH 会话注册表 (lazy 创建, 死链被动失效), 不知道 gRPC / xray.
 *
 * @author nook
 */
public interface SshSessionManager {

    /**
     * 拿一个就绪 SSH 会话, 不存在或已死则现场建; 失败立即抛 BACKEND_UNREACHABLE 不重试.
     *
     * @param serverId resource_server.id
     * @return SshSession
     */
    SshSession acquire(String serverId);

    /**
     * 主动失效缓存的 session, 下次 acquire 用最新凭据重建.
     *
     * @param serverId resource_server.id
     */
    void invalidate(String serverId);

    /**
     * 全量 session 状态快照, 给运维查询接口用.
     *
     * @return Map (serverId → Snapshot)
     */
    Map<String, SshSession.Snapshot> snapshot();

    /**
     * 一次性会话: 临时 cred (不入 resource_server 表, 如 IP 池落地节点) 跑完即关, 不进 cache.
     *
     * @param cred   一次性凭据
     * @param action 业务回调
     * @param <T>    返回类型
     * @return action 的返回值
     */
    <T> T runAdHoc(ServerCredentialDTO cred, Function<SshSession, T> action);

    /**
     * runAdHoc 的 void 版本.
     *
     * @param cred   一次性凭据
     * @param action 业务回调
     */
    default void runAdHocVoid(ServerCredentialDTO cred, Consumer<SshSession> action) {
        runAdHoc(cred, s -> {
            action.accept(s);
            return null;
        });
    }
}
