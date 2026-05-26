package com.nook.biz.agent.service;

import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.function.Consumer;

/**
 * Agent SSH 自动装机 Service 接口
 *
 * @author nook
 */
public interface AgentInstallScriptService {

    /**
     * SSH 自动装机
     *
     * @param sourceId 装机源 (resource_server.id)
     * @param reqVO    装机参数
     * @param lineSink 日志回调
     */
    void installStreaming(String sourceId, AgentInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 获得装机元信息
     *
     * @param role     角色
     * @param sourceId 装机源 (可空)
     * @return 装机元信息
     */
    AgentInstallMetaRespVO getInstallMeta(AgentRole role, String sourceId);

    /**
     * 流式 SSH 自动装机 (Controller 单调一行)
     *
     * @param sourceId 装机源 (resource_server.id)
     * @param reqVO    装机参数
     * @return 流式响应
     */
    ResponseBodyEmitter installStream(String sourceId, AgentInstallReqVO reqVO);
}
