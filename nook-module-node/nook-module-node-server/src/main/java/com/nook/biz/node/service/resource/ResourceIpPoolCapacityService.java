package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCapacityUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCapacityDO;

/**
 * IP 池容量监控 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolCapacityService {

    /**
     * 按 ipId 取容量行
     *
     * @param ipId IP 池编号
     * @return 容量子表 DO; null = 未初始化 (理论不应出现, createIpPool 时已 insert 空行)
     */
    ResourceIpPoolCapacityDO get(String ipId);

    /**
     * 更新容量配置 (admin 改限速 / 月流量 / 重置策略)
     *
     * @param ipId  IP 池编号
     * @param reqVO 更新入参
     */
    void update(String ipId, ResourceIpPoolCapacityUpdateReqVO reqVO);
}
