package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 线路机列表项 Response VO (主表 + agent 运行时聚合)
 *
 * @author nook
 */
@Data
public class ServerFrontlineListItemRespVO {

    private String id;
    private String name;

    /** SSH 主机 (credential 子表). */
    private String host;

    /** 区域码 (e.g., JP-TYO); 前端按 code 查字典拿 flagEmoji / displayName. */
    private String region;

    /** 装机生命周期 {@link ResourceServerLifecycleEnum} */
    private String lifecycleState;

    /** agent 上报的版本号; null = 从未上报心跳 (装机未完成). */
    private String agentVersion;

    /** xray 安装版本 (e.g., v26.3.27); null = 未装 xray. */
    private String xrayVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatAt;

    /** 0=健康 1=暂时不健康 (3-5min 心跳缺失). */
    private Integer tempUnhealthy;

    /** 距上次心跳秒数; null = 从未心跳. */
    private Long elapsedSec;

    /** agent 在线状态 {@link AgentOnlineState} */
    private String onlineState;

    /** 运行时配置同步状态 {@link AgentConfigSyncState} */
    private String configSyncState;

    /** 月度流量配额 GB; 0/null = 不限. */
    private Integer monthlyTrafficGb;

    /** 当周期下行字节. */
    private Long rxBytes;

    /** 当周期上行字节. */
    private Long txBytes;

    /** 当周期已用流量字节 = rx + tx. */
    private Long usedTrafficBytes;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;
}
