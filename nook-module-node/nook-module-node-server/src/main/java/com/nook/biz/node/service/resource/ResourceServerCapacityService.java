package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;

/**
 * 服务器容量 Service 接口
 *
 * @author nook
 */
public interface ResourceServerCapacityService {

    /**
     * 获得服务器容量
     *
     * @param serverId 服务器编号
     * @return 服务器容量
     */
    ResourceServerCapacityDO get(String serverId);

    /**
     * 更新业务阈值
     *
     * @param serverId 服务器编号
     * @param reqVO    阈值入参
     */
    void updateQuota(String serverId, ResourceServerCapacityUpdateReqVO reqVO);
}
