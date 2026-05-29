package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
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

    /** 服务器 id (主键); FK → resource_server.id. */
    @TableId
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null=不限. 同时是 throttle_state 90% 触发的基数 (业务阈值). */
    private Integer monthlyTrafficGb;

    /** 单 server 客户端数硬上限; 0=不限. allocator 候选过滤 + xray inbound 客户数闸. */
    private Integer clientMaxCount;

    /** 当周期下行字节 (vnstat rx 累计; Agent push). */
    private Long rxBytes;

    /** 当周期上行字节 (vnstat tx 累计; Agent push). */
    private Long txBytes;

    /** 当周期已用流量 = rx + tx (Agent push 时同步更新; 老查询继续可用). UI 换算 GB/MB. */
    private Long usedTrafficBytes;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    private String quotaResetPolicy;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;

    /** 线路机出站接口实际限速 Mbps; 0=不限; agent 跑 tc qdisc 落实. */
    private Integer bandwidthLimitMbps;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
