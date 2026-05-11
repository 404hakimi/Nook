package com.nook.framework.ssh.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * 一次远程命令执行的结果汇总.
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public class SshExecResult {

    /** 远端进程退出码. */
    private final int exitCode;

    /** 标准输出; 流式执行下为空串 (已被 lineConsumer 实时消费). */
    private final String stdout;

    /** 标准错误. */
    private final String stderr;

    /** 端到端耗时. */
    private final Duration elapsed;
}
