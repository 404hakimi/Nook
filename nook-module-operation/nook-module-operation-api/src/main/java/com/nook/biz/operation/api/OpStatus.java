package com.nook.biz.operation.api;

/**
 * op_log.status 取值; QUEUED/RUNNING 为活跃态, 其余为终态.
 *
 * @author nook
 */
public enum OpStatus {

    /** 已入队待执行 */
    QUEUED,
    /** worker 拾起执行中 */
    RUNNING,
    /** 成功结束 */
    DONE,
    /** 业务异常或内部异常结束 */
    FAILED,
    /** 在 QUEUED 期间被用户取消 */
    CANCELLED,
    /** 超时被 watchdog 强切 */
    TIMED_OUT,
    ;

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }
}
