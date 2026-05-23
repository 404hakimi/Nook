package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerDnsUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;

/**
 * 服务器 DNS 绑定 Service 接口
 *
 * @author nook
 */
public interface ResourceServerDnsService {

    ResourceServerDnsDO get(String serverId);

    void create(String serverId, ResourceServerDnsUpdateReqVO reqVO);

    void update(String serverId, ResourceServerDnsUpdateReqVO reqVO);
}
