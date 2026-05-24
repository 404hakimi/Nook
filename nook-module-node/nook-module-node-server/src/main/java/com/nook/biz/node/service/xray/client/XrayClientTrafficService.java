package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;

/**
 * Xray 客户端流量 Service 接口
 *
 * @author nook
 */
public interface XrayClientTrafficService {

    /**
     * 获得客户端流量与配额
     *
     * @param id 客户端编号
     * @return 流量与配额
     */
    XrayClientTrafficRespVO getXrayClientTraffic(String id);

    /**
     * 重置客户端累计流量
     *
     * @param id 客户端编号
     */
    void resetXrayClientTraffic(String id);
}
