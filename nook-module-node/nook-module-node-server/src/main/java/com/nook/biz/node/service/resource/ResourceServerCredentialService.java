package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;

/**
 * 服务器 SSH 凭据 Service 接口
 *
 * @author nook
 */
public interface ResourceServerCredentialService {

    /**
     * 获得 SSH 凭据
     *
     * @param serverId 服务器编号
     * @return SSH 凭据
     */
    ResourceServerCredentialDO get(String serverId);

    /**
     * 获得 SSH 凭据 (必存)
     *
     * @param serverId 服务器编号
     * @return SSH 凭据
     */
    ResourceServerCredentialDO requireByServerId(String serverId);

    /**
     * 创建 SSH 凭据
     *
     * @param serverId 服务器编号
     * @param reqVO    凭据入参
     */
    void create(String serverId, ResourceServerCredentialUpdateReqVO reqVO);

    /**
     * 更新 SSH 凭据
     *
     * @param serverId 服务器编号
     * @param reqVO    凭据入参
     */
    void update(String serverId, ResourceServerCredentialUpdateReqVO reqVO);
}
