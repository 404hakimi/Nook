package com.nook.biz.agent.api.enums;

/**
 * Agent 任务状态枚举
 *
 * @author nook
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
