package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerDnsUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;

/**
 * 服务器 DNS 绑定 Service 接口
 *
 * @author nook
 */
public interface ResourceServerDnsService {

    /**
     * 获得服务器 DNS 绑定
     *
     * @param serverId 服务器编号
     * @return DNS 绑定
     */
    ResourceServerDnsDO get(String serverId);

    /**
     * 创建服务器 DNS 绑定
     *
     * @param serverId 服务器编号
     * @param reqVO    DNS 入参
     */
    void create(String serverId, ResourceServerDnsUpdateReqVO reqVO);

    /**
     * 更新服务器 DNS 绑定
     *
     * @param serverId 服务器编号
     * @param reqVO    DNS 入参
     */
    void update(String serverId, ResourceServerDnsUpdateReqVO reqVO);
}
