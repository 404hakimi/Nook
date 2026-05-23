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
     * 取容量
     *
     * @param serverId server 主键
     * @return DO; 不存在返 null
     */
    ResourceServerCapacityDO get(String serverId);

    /**
     * 更新业务阈值 (月流量 / 限定带宽); rx/tx/used 由 agent NIC 上报路径走 applyNicTraffic
     *
     * @param serverId server 主键
     * @param reqVO    业务阈值
     */
    void updateQuota(String serverId, ResourceServerCapacityUpdateReqVO reqVO);
}
