package com.nook.biz.agent.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 角色枚举; 跟 nook-agent 二进制 + Go cmd/* 子命令对齐.
 * code 字段是落库 / 跨进程线协议形式 (lowercase), enum name 是 Java 代码内部用.
 */
@Getter
@RequiredArgsConstructor
public enum AgentRole {

    /** 入口线路机, 跑 xray; agentVersion 前缀 "frontline-". */
    FRONTLINE(Codes.FRONTLINE),
    /** 落地机, 跑 socks5; agentVersion 前缀 "landing-". */
    LANDING(Codes.LANDING);

    /** 落库 / API 用的字符串值. */
    private final String code;

    /**
     * 由 code 反查枚举.
     *
     * @param code lowercase 字符串
     * @return 匹配的枚举值; 无匹配抛 IllegalArgumentException
     */
    public static AgentRole fromCode(String code) {
        for (AgentRole r : values()) {
            if (r.code.equals(code)) return r;
        }
        throw new IllegalArgumentException("unknown agent role code: " + code);
    }

    /**
     * 检查 code 是否合法.
     *
     * @param code 待检查字符串
     * @return code 命中任意枚举返 true
     */
    public static boolean isValid(String code) {
        for (AgentRole r : values()) {
            if (r.code.equals(code)) return true;
        }
        return false;
    }

    /** 给 @RequestParam(defaultValue=...) / @Pattern(regexp=...) 等编译期常量用; 普通代码用 enum.code(). */
    public static final class Codes {
        public static final String FRONTLINE = "frontline";
        public static final String LANDING = "landing";
        /** 用作 @Pattern 校验; @RequestParam role 字段的合法值. */
        public static final String PATTERN = FRONTLINE + "|" + LANDING;

        private Codes() {}
    }
}
