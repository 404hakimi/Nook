package com.nook.biz.agent.api.enums;

/**
 * Agent 运行时配置同步状态枚举
 *
 * @author nook
 */
public enum AgentConfigSyncState {
    /** agent_runtime_config 表无该 server 行; 装机时由后端首次写入. */
    NEVER_CONFIGURED,
    /** stored md5 == applied_md5, agent 已应用最新版. */
    SYNCED,
    /** stored md5 != applied_md5, 等待 agent 心跳后 backend 派 config_reload task. */
    PENDING;

    public static AgentConfigSyncState fromState(String state) {
        if (state == null) {
            return null;
        }
        for (AgentConfigSyncState e : values()) {
            if (e.name().equals(state)) {
                return e;
            }
        }
        return null;
    }

    public boolean matches(String state) {
        return this.name().equals(state);
    }
}
