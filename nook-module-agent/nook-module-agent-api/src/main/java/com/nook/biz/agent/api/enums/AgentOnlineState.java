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
    /** 心跳距今 180s ~ 300s; 暂时不健康, 暂停分配. */
    TEMP_UNHEALTHY,
    /** 心跳距今 ≥ 300s, 视为真故障. */
    OFFLINE,
    /** 从未收到过心跳 (last_heartbeat_at = NULL); 未装机或装机中. */
    NEVER;

    private static final int WARN_SEC = 60;
    private static final int TEMP_UNHEALTHY_SEC = 180;
    private static final int OFFLINE_SEC = 300;

    /**
     * 按心跳延迟推导在线状态 (纯看距上次心跳秒数; 状态读时实时算, 不依赖持久化标志).
     *
     * @param elapsedSec 距上次心跳秒数; null 视为从未上报
     * @return 对应状态枚举
     */
    public static AgentOnlineState classify(Long elapsedSec) {
        if (elapsedSec == null) return NEVER;
        if (elapsedSec >= OFFLINE_SEC) return OFFLINE;
        if (elapsedSec >= TEMP_UNHEALTHY_SEC) return TEMP_UNHEALTHY;
        if (elapsedSec >= WARN_SEC) return WARN;
        return ONLINE;
    }
}
