package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;

/**
 * Xray 客户端流量域 Service: 对外查询 / 重置.
 *
 * <p>定时采样 (sampleServerTraffic) 在 {@link XrayClientTrafficSampleService}, 内部 job 触发, 不暴露给业务侧;
 * 这里只放面向 controller / 管理后台的"查 + 重置"两个动作.
 *
 * @author nook
 */
public interface XrayClientTrafficService {

    /**
     * 获得Xray客户端-流量与配额
     *
     * @param id xray客户端ID
     * @return XrayClientTrafficRespVO
     */
    XrayClientTrafficRespVO getXrayClientTraffic(String id);

    /**
     * 重置 Xray 客户端累计流量: 清 DB 累计, 同时把远端当前累计值设为新的基线.
     *
     * @param id xray 客户端 ID
     */
    void resetXrayClientTraffic(String id);
}
