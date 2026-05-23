package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池 agent 心跳 + 健康 DO (1:1, 高频)
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_runtime")
public class ResourceIpPoolRuntimeDO {

    @TableId
    private String ipId;

    /** agent 上次心跳; null=从未. */
    private LocalDateTime lastHeartbeatAt;

    /** 3-5min 心跳缺失暂时不健康 (allocator 跳过). */
    private Integer tempUnhealthy;

    private String agentVersion;

    /** agent 源 IP (反查网络). */
    private String lastAgentSeenIp;

    /** 连续心跳缺失次数 (心跳到达清零). */
    private Integer consecutiveMiss;

    /** 兼容旧字段; agent 装好后由 health probe 写. */
    private LocalDateTime lastHealthAt;

    private LocalDateTime updatedAt;
}
