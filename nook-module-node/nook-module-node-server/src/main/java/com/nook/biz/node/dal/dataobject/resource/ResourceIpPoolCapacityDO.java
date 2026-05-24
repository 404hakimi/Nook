package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池容量监控 (1:1 跟 resource_ip_pool); agent 上报流量后写入.
 *
 * <p>跟 {@link ResourceServerCapacityDO} 同语义: 控制实际带宽 + 月流量阈值 + 累计 + throttle.
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_capacity")
public class ResourceIpPoolCapacityDO {

    @TableId
    private String ipId;

    /** dante 实际限速 Mbps; 0=不限; agent 改 sockd.conf 落实. */
    private Integer bandwidthLimitMbps;

    /** 月流量上限 GB; null/0=不限. throttle 90% 触发基数. */
    private Integer monthlyTrafficGb;

    /** 当周期累计已用 = rx + tx (Agent push 时同步更新). UI 换算 GB/MB. */
    private Long usedTrafficBytes;

    /** 当周期下行字节 (vnstat rx 累计; Agent push). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计; Agent push). */
    private Long txBytes;

    /**
     * 周期重置策略: CALENDAR_MONTH / BILLING_CYCLE / FIXED.
     *
     * <ul>
     *   <li>CALENDAR_MONTH: 每月 1 号重置 (默认)
     *   <li>BILLING_CYCLE: 按 billing.billing_cycle_day
     *   <li>FIXED: 永不
     * </ul>
     */
    private String quotaResetPolicy;

    /** NORMAL / THROTTLED; used 90% → THROTTLED (后续做状态机). */
    private String throttleState;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
