package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;

/**
 * 服务器账面 Service 接口
 *
 * @author nook
 */
public interface ResourceServerBillingService {

    /**
     * 获得服务器账面
     *
     * @param serverId 服务器编号
     * @return 服务器账面
     */
    ResourceServerBillingDO get(String serverId);

    /**
     * 创建服务器账面
     *
     * @param serverId 服务器编号
     * @param reqVO    账面入参
     */
    void create(String serverId, ResourceServerBillingUpdateReqVO reqVO);

    /**
     * 更新服务器账面
     *
     * @param serverId 服务器编号
     * @param reqVO    账面入参
     */
    void update(String serverId, ResourceServerBillingUpdateReqVO reqVO);
}
