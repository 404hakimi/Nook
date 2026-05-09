package com.nook.biz.node.framework.ssh;

import com.nook.biz.node.framework.ssh.dto.SshExecResult;

import java.time.Duration;
import java.util.function.Consumer;

/** SSH 命令通道; 跑命令 / 流式跑命令 / 上传文本. */
public interface SshChannel {

    /** 跑命令; 退出码非 0 抛 BACKEND_OPERATION_FAILED. */
    SshExecResult exec(String cmd, Duration timeout);

    /** 流式跑命令, 每读到一行 stdout 回调 lineConsumer; stderr 不走回调, 调用方需手动 2>&1. */
    SshExecResult execStream(String cmd, Duration timeout, Consumer<String> lineConsumer);

    /** 把字符串写到远端路径并 chmod 600; 走 base64+exec, 不依赖 shell 转义. */
    void uploadString(String remotePath, String content, Duration timeout);
}
