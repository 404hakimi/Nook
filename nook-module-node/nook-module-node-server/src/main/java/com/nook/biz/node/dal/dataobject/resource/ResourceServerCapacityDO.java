package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import lombok.Data;

import java.time.LocalDate;
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

    /** 服务器 id (主键). */
    @TableId
    private String serverId;

    /** 机房 NIC 月配额 GB; 0/null=不限. 同时是 throttle_state 90% 触发的基数 (业务阈值). */
    private Integer monthlyTrafficGb;

    /** 当周期入站字节 (agent 读网卡累计上报). */
    private Long rxBytes;

    /** 当周期出站字节 (agent 读网卡累计上报). */
    private Long txBytes;

    /** 当周期已用流量 = 入站 + 出站 (增量累加; 重置日清零). UI 换算 GB/MB. */
    private Long usedTrafficBytes;

    /** socks5 业务流量累计字节 (双向之和; 仅落地机, agent 读防火墙计数器上报; 已排除 agent/系统流量). 用户套餐计量用, 累计值不随周期清零. */
    private Long bizUsedBytes;

    /** 上次上报的网卡累计入站字节(算增量用的基准值); null=尚未建立基准. */
    private Long lastCumRxBytes;

    /** 上次上报的网卡累计出站字节(算增量用的基准值). */
    private Long lastCumTxBytes;

    /** 当前流量周期起点(我方重置日). */
    private LocalDate periodStart;

    /** 周期重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    private String quotaResetPolicy;

    /** 按月流量重置日 1-28; 仅 MONTHLY 生效, FIXED 忽略; 取不到时按 1 号. */
    private Integer resetDay;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;

    /** 出站带宽 Mbps. 落地机: agent tc 在出口网卡真实限速(取 min(套餐,本值), 0=不限); 线路机: 出站带宽容量, 供套餐分配不超卖(预留~10%), 0/空=不参与分配, 不整形. */
    private Integer bandwidthLimitMbps;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
