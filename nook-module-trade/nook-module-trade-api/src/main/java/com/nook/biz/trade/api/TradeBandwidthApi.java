package com.nook.biz.trade.api;

/**
 * 落地机限速值解析契约 (agent 拉 desired-bandwidth 时用).
 *
 * <p>带宽不再存在 xray 表上, 一律从套餐派生: 落地机 1:1, 取占用它的 ACTIVE 订阅的套餐带宽.
 *
 * @author nook
 */
public interface TradeBandwidthApi {

    /**
     * 某落地机当前应施加的 tc 限速 (Mbps); 落地 1:1, 取占用它的 ACTIVE 订阅的套餐 bandwidthMbps.
     *
     * @param landingServerId 落地机 server id
     * @return 限速 Mbps; 0 = 不限 (无订阅占用 / 套餐不限速)
     */
    int getLandingDesiredBandwidthMbps(String landingServerId);
}
