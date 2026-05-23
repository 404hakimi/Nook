package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;

/**
 * IP 池 dante 配置 + 限速 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolSocks5Service {

    /**
     * 取 dante 配置
     *
     * @param ipId IP 池 id
     * @return DO; 不存在返 null
     */
    ResourceIpPoolSocks5DO get(String ipId);

    /**
     * 更新 dante 配置; socks5Password 留空 = 保留原值; 改 bandwidthLimitMbps 时走链路校验.
     *
     * @param ipId  IP 池 id
     * @param reqVO 待保存
     */
    void update(String ipId, ResourceIpPoolSocks5UpdateReqVO reqVO);
}
