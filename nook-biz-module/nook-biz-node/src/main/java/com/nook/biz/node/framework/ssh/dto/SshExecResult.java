package com.nook.biz.node.framework.ssh.dto;

import java.time.Duration;

/**
 * 一次远程命令执行的结果汇总.
 *
 * @author nook
 */
public record SshExecResult(
        /** 远端进程退出码 */
        int exitCode,
        /** 标准输出; 流式执行下为空串 (已被 lineConsumer 实时消费) */
        String stdout,
        /** 标准错误 */
        String stderr,
        /** 端到端耗时 */
        Duration elapsed
) {
}
