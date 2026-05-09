package com.nook.biz.node.framework.ssh.dto;

import java.time.Duration;

/** 一次远程命令执行的结果汇总; 流式执行下 stdout/stderr 可能为空 (caller 已通过 lineConsumer 实时消费). */
public record SshExecResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration elapsed
) {
}
