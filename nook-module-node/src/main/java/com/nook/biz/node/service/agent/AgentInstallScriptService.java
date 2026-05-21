package com.nook.biz.node.service.agent;

import java.util.function.Consumer;

/**
 * Agent SSH 自动装机; backend 直接 ssh 到目标 server 跑 install/nook-agent.sh.tmpl, 流式回吐日志.
 *
 * @author nook
 */
public interface AgentInstallScriptService {

    /**
     * SSH 自动装机 (流式).
     *
     * <p>每行日志通过 lineSink 回吐, controller 转发到 ResponseBodyEmitter.
     * 复用 resource_server 已存的 SSH 凭据 (SshSessionScope.INSTALL).
     * 装机过程: 重置 agent_token → SSH 跑 nook-agent.sh.tmpl → agent 心跳上来.
     *
     * @param serverId resource_server.id
     * @param role     frontline (跑 xray) / landing (跑 socks5)
     * @param lineSink 日志逐行回调
     */
    void installStreaming(String serverId, String role, Consumer<String> lineSink);
}
