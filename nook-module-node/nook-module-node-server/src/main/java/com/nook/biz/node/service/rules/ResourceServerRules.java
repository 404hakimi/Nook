package com.nook.biz.node.service.rules;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.common.utils.unit.TrafficUnitUtils;

/**
 * 落地机 / 线路机 纯判定规则 (无状态, 值入参不依赖 DO)
 *
 * @author nook
 */
public final class ResourceServerRules {

    /** 重置日缺省值; 取不到账单日时用 1 号. */
    public static final int DEFAULT_RESET_DAY = 1;

    /** 可用比例缺省值%; 默认留 10% 冗余吃换机延迟 / 装机流量 / 口径误差. */
    public static final int DEFAULT_USABLE_PERCENT = 90;

    private ResourceServerRules() {}

    /**
     * 落地机带宽是否达到套餐规格 (机器侧 0 / 空 = 不限, 视为达标)
     *
     * @param bandwidthMbps    落地机带宽上限 Mbps (null / ≤0 = 不限)
     * @param minBandwidthMbps 套餐要求带宽 Mbps
     * @return true = 达标
     */
    public static boolean meetsBandwidthSpec(Integer bandwidthMbps, int minBandwidthMbps) {
        return ObjectUtil.isNull(bandwidthMbps) || bandwidthMbps <= 0 || bandwidthMbps >= minBandwidthMbps;
    }

    /**
     * 本月剩余预算是否撑得起一个套餐: 可用字节 − 当周期已用 ≥ 套餐GB × 2 换字节
     *
     * <p>套餐 GB 是用户口径, 机器进出双向计量, 需求按 ×2 估; totalGb 空 / ≤0 = 不限额, 视为通过.
     *
     * @param totalGb          落地机月流量配额 GB (null / ≤0 = 不限)
     * @param usablePercent    月配额实际可用比例% (空 / 越界兜底 90)
     * @param currentUsedBytes 当周期机器已用字节 (null = 0)
     * @param planTrafficGb    套餐月流量 GB
     * @return true = 预算足够
     */
    public static boolean hasTrafficBudget(Integer totalGb, Integer usablePercent,
                                           Long currentUsedBytes, int planTrafficGb) {
        if (ObjectUtil.isNull(totalGb) || totalGb <= 0) {
            return true;
        }
        long used = ObjectUtil.isNull(currentUsedBytes) ? 0L : currentUsedBytes;
        return usableBytes(totalGb, usablePercent) - used >= TrafficUnitUtils.gbToBytes(planTrafficGb * 2L);
    }

    /**
     * 是否已置限流
     *
     * @param throttleState 限流状态
     * @return 是否限流中
     */
    public static boolean isThrottled(String throttleState) {
        return ResourceServerThrottleStateEnum.THROTTLED.matches(throttleState);
    }

    /**
     * 月配额实际可用字节 (限流阈值) = totalGb × usablePercent / 100
     *
     * @param totalGb       月流量配额 GB (须 >0, 不限额由调用方先判)
     * @param usablePercent 月配额实际可用比例% (空 / 越界兜底 90)
     * @return 可用字节
     */
    public static long usableBytes(int totalGb, Integer usablePercent) {
        return TrafficUnitUtils.gbToBytes(totalGb) * resolveUsablePercent(usablePercent) / 100;
    }

    /** 可用比例归一 (1..100); 空值 / 越界用缺省 90 (仅本类 usableBytes 用). */
    private static int resolveUsablePercent(Integer usablePercent) {
        return (ObjectUtil.isNull(usablePercent) || usablePercent < 1 || usablePercent > 100)
                ? DEFAULT_USABLE_PERCENT : usablePercent;
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
