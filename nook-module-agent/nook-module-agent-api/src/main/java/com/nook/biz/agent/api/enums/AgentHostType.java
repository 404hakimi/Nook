package com.nook.biz.agent.api.enums;

/**
 * Agent 任务目标主机类型
 *
 * @author nook
 */
public enum AgentHostType {
    /** 线路机 (resource_server.id). */
    SERVER,
    /** 落地机 (resource_ip_pool.id). */
    IP_POOL;

    /** 等价于 name(), 语义化别名 — 跟其它 enum.code() 保持一致写法. */
    public String code() {
        return name();
    }
}
