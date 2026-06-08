package com.nook.biz.node.service.rules;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;

/**
 * 落地机 / 线路机 纯判定规则 (无状态, 值入参不依赖 DO)
 *
 * @author nook
 */
public final class ResourceServerRules {

    /** 重置日缺省值; 取不到账单日时用 1 号. */
    private static final int DEFAULT_RESET_DAY = 1;

    private ResourceServerRules() {}

    /**
     * 落地机配额 / 带宽是否达到套餐规格 (机器侧 0 / 空 = 不限, 视为达标)
     *
     * @param totalGb          落地机月流量配额 GB (null / ≤0 = 不限)
     * @param bandwidthMbps    落地机带宽上限 Mbps (null / ≤0 = 不限)
     * @param minTrafficGb     套餐要求月流量 GB
     * @param minBandwidthMbps 套餐要求带宽 Mbps
     * @return true = 达标
     */
    public static boolean meetsPlanSpec(Integer totalGb, Integer bandwidthMbps,
                                        int minTrafficGb, int minBandwidthMbps) {
        if (ObjectUtil.isNotNull(totalGb) && totalGb > 0 && totalGb < minTrafficGb) {
            return false;
        }
        return ObjectUtil.isNull(bandwidthMbps) || bandwidthMbps <= 0 || bandwidthMbps >= minBandwidthMbps;
    }

    /** 测量行是否已置限流 (值入参). */
    public static boolean isThrottled(String throttleState) {
        return ResourceServerThrottleStateEnum.THROTTLED.matches(throttleState);
    }

    /**
     * 我方重置日归一 (clamp 1..28); 取不到用缺省 1 号
     *
     * @param resetDay 配额配置的重置日 (可空)
     * @return 归一后的重置日 (1..28)
     */
    public static int resolveResetDay(Integer resetDay) {
        return (ObjectUtil.isNull(resetDay) || resetDay < 1 || resetDay > 28) ? DEFAULT_RESET_DAY : resetDay;
    }
}
