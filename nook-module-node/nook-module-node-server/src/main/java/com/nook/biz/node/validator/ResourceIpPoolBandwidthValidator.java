package com.nook.biz.node.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IP 池链路带宽校验
 *
 * @author nook
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceIpPoolBandwidthValidator {

    /** 线路机预留率: Σ落地机限速 ≤ 线路机限速 × (1 - RESERVATION_RATIO). */
    public static final double BANDWIDTH_RESERVATION_RATIO = 0.10;

    /**
     * 校验链路带宽守恒: 该 IP 所在的所有 SKU 池内 Σ(落地机限速) ≤ min(线路机限速) × 0.9
     *
     * <p>实现依赖 plan_sku_resource 关联表 (Sprint 1+ 建); 当前仅做字段级 @Min(0) 校验,
     * 链路校验留 TODO; admin UI 已展示静态提示, 实际跑链路校验等 plan_sku_resource 接入.
     *
     * @param ipId  IP 池 id; 校验时排除自身的旧 limit
     * @param newMbps 待校验的新限速 Mbps
     */
    public void validateLinkCapacity(String ipId, Integer newMbps) {
        // TODO Sprint 1: plan_sku_resource 表建好后:
        //   1. 查 ip 所在的所有 SKU 池 (plan_sku_resource WHERE resource_type='IP_POOL' AND resource_id=ipId)
        //   2. 对每个 SKU 池查 server 池的最小 bandwidth_limit_mbps
        //   3. 对每个 SKU 池查其他 IP 的 bandwidth_limit_mbps 之和
        //   4. 校验 Σ + newMbps ≤ min(server limit) × (1 - RESERVATION_RATIO)
        //   5. 失败抛 BusinessException(LINK_OVERSUBSCRIBED, ...)
        log.debug("[bandwidth-validator] 链路校验跳过 (plan_sku_resource 未建); ipId={} newMbps={}",
                ipId, newMbps);
    }
}
