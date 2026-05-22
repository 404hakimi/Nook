package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器 NIC 容量监控 (中频, Agent 上报 NIC 流量后写入).
 * 跟 {@link ResourceServerDO} 1:1; server_id 即主键.
 *
 * @author nook
 */
@Data
@TableName("resource_server_capacity")
public class ResourceServerCapacityDO {

    @TableId
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null=不限. */
    private Integer monthlyTrafficGb;

    /** 当周期已用 NIC 流量 (字节; Agent push vnstat 月度累计写入). UI 展示前自行换算 GB/MB. */
    private Long usedTrafficBytes;

    /**
     * 周期重置策略: CALENDAR_MONTH / BILLING_CYCLE / FIXED.
     * - CALENDAR_MONTH: 每月 1 号重置
     * - BILLING_CYCLE: 按 resource_server.billing_cycle_day
     * - FIXED: 永不
     */
    private String quotaResetPolicy;

    /** NORMAL / THROTTLED; used 90% → THROTTLED, allocator 跳过. */
    private String throttleState;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
