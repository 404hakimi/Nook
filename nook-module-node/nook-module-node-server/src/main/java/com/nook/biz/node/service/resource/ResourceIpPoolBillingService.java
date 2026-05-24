package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolBillingUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;

/**
 * IP 池账面 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolBillingService {

    /**
     * 获得 IP 池账面
     *
     * @param ipId IP 池编号
     * @return 账面
     */
    ResourceIpPoolBillingDO get(String ipId);

    /**
     * 更新 IP 池账面
     *
     * @param ipId  IP 池编号
     * @param reqVO 账面入参
     */
    void update(String ipId, ResourceIpPoolBillingUpdateReqVO reqVO);
}
