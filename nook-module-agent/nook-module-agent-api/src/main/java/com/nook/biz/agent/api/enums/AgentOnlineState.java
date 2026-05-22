package com.nook.biz.agent.api.enums;

/**
 * Agent 在线状态枚举
 *
 * @author nook
 */
public enum AgentOnlineState {
    /** 心跳距今 < 60s, 健康. */
    ONLINE,
    /** 心跳距今 60s ~ 180s, 轻微延迟. */
    WARN,
    /** 心跳距今 180s ~ 300s, 或 temp_unhealthy=1; 暂时不健康, backend 切流. */
    TEMP_UNHEALTHY,
    /** 心跳距今 ≥ 300s, 视为真故障. */
    OFFLINE,
    /** 从未收到过心跳 (last_heartbeat_at = NULL); 未装机或装机中. */
    NEVER;

    private static final int WARN_SEC = 60;
    private static final int TEMP_UNHEALTHY_SEC = 180;
    private static final int OFFLINE_SEC = 300;

    /**
     * 按心跳延迟 + 健康标志推导在线状态.
     *
     * @param elapsedSec    距上次心跳秒数; null 视为从未上报
     * @param tempUnhealthy resource_server_runtime.temp_unhealthy (1=不健康, 其他=正常)
     * @return 对应状态枚举
     */
    public static AgentOnlineState classify(Long elapsedSec, Integer tempUnhealthy) {
        if (elapsedSec == null) return NEVER;
        if (elapsedSec >= OFFLINE_SEC) return OFFLINE;
        if (elapsedSec >= TEMP_UNHEALTHY_SEC || (tempUnhealthy != null && tempUnhealthy == 1)) return TEMP_UNHEALTHY;
        if (elapsedSec >= WARN_SEC) return WARN;
        return ONLINE;
    }
}
