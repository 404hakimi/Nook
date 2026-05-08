package com.nook.biz.resource.service;

import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.common.web.response.PageResult;

/** 服务器(出口 VPS)管理。 */
public interface ResourceServerService {

    ResourceServer findById(String id);

    PageResult<ResourceServer> page(ResourceServerPageReqVO reqVO);

    ResourceServer create(ResourceServerSaveReqVO reqVO);

    /** 更新；密码字段(sshPassword/sshPrivateKey)传 null 保留旧值, 传值覆盖。 */
    ResourceServer update(String id, ResourceServerSaveReqVO reqVO);

    void delete(String id);

    /**
     * 加载凭据；密码原文一并带出。
     * 仅供模块内/api 边界使用，不要直接交给 Controller 返回。
     */
    ServerCredentialDTO loadCredential(String id);
}
