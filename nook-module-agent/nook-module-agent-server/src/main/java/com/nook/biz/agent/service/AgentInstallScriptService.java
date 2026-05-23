package com.nook.biz.agent.service;

import com.nook.biz.agent.api.enums.AgentHostType;
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
     * SSH 自动装机 (流式); hostId 按 reqVO.hostType 分别去 resource_server / resource_ip_pool 查.
     *
     * @param hostId   server id (frontline) 或 ip_pool id (landing)
     * @param reqVO    装机参数
     * @param lineSink 日志回调
     */
    void installStreaming(String hostId, AgentInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 获得装机元信息 (前端 prefill 用); hostType 决定 hostId 走哪个表.
     *
     * @param role     角色 (frontline / landing)
     * @param hostType SERVER (frontline) / IP_POOL (landing); 可空, role=frontline 默认 SERVER
     * @param hostId   server id 或 ip_pool id; 可空 (用户还没选)
     * @return 装机元信息
     */
    AgentInstallMetaRespVO getInstallMeta(String role, AgentHostType hostType, String hostId);
}
