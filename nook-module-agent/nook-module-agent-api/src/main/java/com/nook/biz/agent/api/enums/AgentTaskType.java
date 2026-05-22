package com.nook.biz.agent.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent task 类型枚举; 跟 go agent 端 executor.Register 的字符串 key 一一对应.
 * Backend dispatch / agent 端反序列化都按此 code 路由.
 */
@Getter
@RequiredArgsConstructor
public enum AgentTaskType {

    /** 健康检查 ping; agent 端固定 echo, 用作探活. */
    PING(Codes.PING),
    /** 升级 agent 二进制; payload 含 url / sha256 / version. */
    AGENT_UPGRADE(Codes.AGENT_UPGRADE),
    /** 改 agent config.yml; payload 含 yaml + md5, 成功后 backend 回写 applied_md5. */
    CONFIG_RELOAD(Codes.CONFIG_RELOAD),
    /** xray 新增 inbound user; payload 含完整 inbound JSON. */
    XRAY_PROVISION_USER(Codes.XRAY_PROVISION_USER),
    /** xray 删除 inbound user; payload 含 inboundTag + email. */
    XRAY_REMOVE_USER(Codes.XRAY_REMOVE_USER),
    /** xray 改 outbound; payload 含新 outbound JSON. */
    XRAY_UPDATE_OUTBOUND(Codes.XRAY_UPDATE_OUTBOUND);

    /** 落库 / 跟 agent 通信用的字符串值. */
    private final String code;

    /** 给 @RequestParam / @Pattern 等编译期常量用; 普通代码用 enum.code(). */
    public static final class Codes {
        public static final String PING = "ping";
        public static final String AGENT_UPGRADE = "agent_upgrade";
        public static final String CONFIG_RELOAD = "config_reload";
        public static final String XRAY_PROVISION_USER = "xray_provision_user";
        public static final String XRAY_REMOVE_USER = "xray_remove_user";
        public static final String XRAY_UPDATE_OUTBOUND = "xray_update_outbound";

        private Codes() {}
    }
}
