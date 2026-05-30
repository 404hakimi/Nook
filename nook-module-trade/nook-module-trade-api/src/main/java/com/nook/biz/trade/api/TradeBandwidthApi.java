package com.nook.biz.trade.api;

/**
 * 落地机限速值解析 Api
 *
 * @author nook
 */
public interface TradeBandwidthApi {

    /**
     * 获得某落地机当前应施加的限速 (Mbps)
     *
     * @param landingServerId 落地机 server 编号
     * @return 限速 Mbps; 0 = 不限 (无订阅占用 / 套餐不限速)
     */
    int getLandingDesiredBandwidthMbps(String landingServerId);
}
