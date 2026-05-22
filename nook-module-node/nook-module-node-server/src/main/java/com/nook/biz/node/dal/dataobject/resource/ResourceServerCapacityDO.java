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

    /** 当周期下行字节 (vnstat rx 累计; Agent push). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计; Agent push). */
    private Long txBytes;

    /** 当周期已用流量 = rx + tx (Agent push 时同步更新; 老查询继续可用). UI 换算 GB/MB. */
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
