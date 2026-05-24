package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;

/**
 * IP 池 SSH 凭据 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolCredentialService {

    /**
     * 获得 SSH 凭据
     *
     * @param ipId IP 池编号
     * @return SSH 凭据
     */
    ResourceIpPoolCredentialDO get(String ipId);

    /**
     * 获得 SSH 凭据 (必存)
     *
     * @param ipId IP 池编号
     * @return SSH 凭据
     */
    ResourceIpPoolCredentialDO requireByIpId(String ipId);

    /**
     * 更新 SSH 凭据
     *
     * @param ipId  IP 池编号
     * @param reqVO 凭据入参
     */
    void update(String ipId, ResourceIpPoolCredentialUpdateReqVO reqVO);
}
