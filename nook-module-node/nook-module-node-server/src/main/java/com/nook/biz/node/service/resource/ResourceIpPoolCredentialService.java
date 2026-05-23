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
     * 取凭据
     *
     * @param ipId IP 池 id
     * @return DO; 不存在返 null
     */
    ResourceIpPoolCredentialDO get(String ipId);

    /**
     * 取凭据; 缺失抛 IP_POOL_SSH_CRED_MISSING.
     *
     * @param ipId IP 池 id
     * @return DO
     */
    ResourceIpPoolCredentialDO requireByIpId(String ipId);

    /**
     * 更新凭据; sshPassword 留空 = 保留原值.
     *
     * @param ipId  IP 池 id
     * @param reqVO 待保存
     */
    void update(String ipId, ResourceIpPoolCredentialUpdateReqVO reqVO);
}
