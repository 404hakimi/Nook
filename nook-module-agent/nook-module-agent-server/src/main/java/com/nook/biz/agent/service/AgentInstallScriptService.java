package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;

import java.util.function.Consumer;

/**
 * Agent SSH 自动装机; backend 直接 ssh 到目标 server 跑 install/nook-agent.sh.tmpl, 流式回吐日志.
 *
 * @author nook
 */
public interface AgentInstallScriptService {

    /**
     * SSH 自动装机 (流式). 表单字段 → backend 拼 yaml → 写到远端 /home/nook-agent/config.yml.
     *
     * @param serverId resource_server.id
     * @param reqVO    表单字段 (role + 各间隔 + xray)
     * @param lineSink 日志逐行回调
     */
    void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 装机会动到的路径 + URL 常量; dialog 顶部 readonly 展示让 admin 心里有数.
     * role=frontline + serverId 非空时附带 xray bin / api_port (xray_node 读); 缺/landing 时不填.
     */
    AgentInstallMetaRespVO getInstallMeta(String role, String serverId);
}
