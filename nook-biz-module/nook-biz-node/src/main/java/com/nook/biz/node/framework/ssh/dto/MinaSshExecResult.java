package com.nook.biz.node.framework.ssh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;

/**
 * 一次远程命令执行的结果汇总.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class MinaSshExecResult {

    /** 远端进程退出码. */
    private int exitCode;

    /** 标准输出; 流式执行下为空串 (已被 lineConsumer 实时消费). */
    private String stdout;

    /** 标准错误. */
    private String stderr;

    /** 端到端耗时. */
    private Duration elapsed;
}
