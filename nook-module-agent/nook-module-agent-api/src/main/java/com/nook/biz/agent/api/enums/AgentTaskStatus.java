package com.nook.biz.agent.api.enums;

/**
 * Agent task 队列状态机; agent_task.status 列 + 上下行接口 status 字段同一组值.
 * enum name 即 code (uppercase), 直接 .name() 即可序列化.
 */
public enum AgentTaskStatus {
    /** 待 agent 拾取; backend INSERT 时初始状态. */
    PENDING,
    /** Agent 已通过 /tasks 轮询 CAS 拾取, 正在执行. */
    PICKED,
    /** Agent 上报 task-result, status=SUCCESS. */
    SUCCESS,
    /** Agent 上报 task-result, status=FAILED; resultPayload 含 error 信息. */
    FAILED;

    /** 等价于 name(), 语义化别名 — 跟其它 enum.code() 保持一致写法. */
    public String code() {
        return name();
    }
}
