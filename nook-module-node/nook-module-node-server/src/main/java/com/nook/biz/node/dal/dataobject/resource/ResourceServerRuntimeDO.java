package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器运行时状态 (高频, Agent 每 1min 心跳更新).
 * 拆出独立表避免高频 UPDATE 污染主表 buffer pool. 跟 {@link ResourceServerDO} 1:1.
 *
 * @author nook
 */
@Data
@TableName("resource_server_runtime")
public class ResourceServerRuntimeDO {

    /** 服务器 id (主键). */
    @TableId
    private String serverId;

    /** agent 上次心跳时间. */
    private LocalDateTime lastHeartbeatAt;

    /** 3-5min 心跳缺失暂时性不健康, allocator 跳过; 0=健康 1=不健康. */
    private Integer tempUnhealthy;

    /** agent 上报的版本号. */
    private String agentVersion;

    /** agent 上报源 IP (反向检测网络异常). */
    private String lastAgentSeenIp;

    /** 连续心跳缺失次数; 心跳到达时清零. */
    private Integer consecutiveMiss;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
