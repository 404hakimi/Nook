package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;

/**
 * 服务器账面 Service 接口
 *
 * @author nook
 */
public interface ResourceServerBillingService {

    ResourceServerBillingDO get(String serverId);

    void create(String serverId, ResourceServerBillingUpdateReqVO reqVO);

    void update(String serverId, ResourceServerBillingUpdateReqVO reqVO);
}
