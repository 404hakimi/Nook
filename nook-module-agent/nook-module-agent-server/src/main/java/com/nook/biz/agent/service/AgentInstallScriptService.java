package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;

import java.util.function.Consumer;

/**
 * Agent SSH 自动装机 Service 接口
 *
 * @author nook
 */
public interface AgentInstallScriptService {

    /**
     * SSH 自动装机 (流式)
     *
     * @param serverId server 编号
     * @param reqVO    装机参数
     * @param lineSink 日志回调
     */
    void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 获得装机元信息
     *
     * @param role     角色 (frontline / landing)
     * @param serverId server 编号 (可选)
     * @return 装机元信息
     */
    AgentInstallMetaRespVO getInstallMeta(String role, String serverId);
}
