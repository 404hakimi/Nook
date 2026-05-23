package com.nook.biz.node.service.resource;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;

/**
 * 服务器容量 Service 接口
 *
 * @author nook
 */
public interface ResourceServerCapacityService {

    /**
     * 取容量
     *
     * @param serverId server 主键
     * @return DO; 不存在返 null
     */
    ResourceServerCapacityDO get(String serverId);
}
