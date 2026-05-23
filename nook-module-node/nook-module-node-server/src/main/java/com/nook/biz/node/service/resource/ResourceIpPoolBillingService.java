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
     * 取账面
     *
     * @param ipId IP 池 id
     * @return DO; 不存在返 null
     */
    ResourceIpPoolBillingDO get(String ipId);

    /**
     * 更新账面
     *
     * @param ipId  IP 池 id
     * @param reqVO 待保存
     */
    void update(String ipId, ResourceIpPoolBillingUpdateReqVO reqVO);
}
