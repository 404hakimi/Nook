package com.nook.biz.node.service.agent;

import com.nook.biz.node.controller.resource.vo.AgentInstallReqVO;

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
     * <p>复用 resource_server 已存的 SSH 凭据 (SshSessionScope.INSTALL).
     * 装机过程: 重置 agent_token → 把 token 拼入用户传的 configYaml → SSH 跑装机脚本 → agent 上线.
     *
     * @param serverId resource_server.id
     * @param reqVO    role + 用户填的完整 agent yaml (含 {{AGENT_TOKEN}} 占位符)
     * @param lineSink 日志逐行回调
     */
    void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 取默认 agent yaml 模板; dialog 打开时预填给用户编辑.
     * api_url 用 nook.backend.public-url 填好, api_token 留 {{AGENT_TOKEN}} 占位.
     */
    String defaultConfigYaml(String role);
}
