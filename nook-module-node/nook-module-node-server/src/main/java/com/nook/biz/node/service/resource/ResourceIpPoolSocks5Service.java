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
     * 获得 dante 配置 + 限速
     *
     * @param ipId IP 池编号
     * @return socks5 配置
     */
    ResourceIpPoolSocks5DO get(String ipId);

    /**
     * 更新 dante 配置 + 限速
     *
     * @param ipId  IP 池编号
     * @param reqVO socks5 入参
     */
    void update(String ipId, ResourceIpPoolSocks5UpdateReqVO reqVO);
}
