package com.nook.biz.node.framework.ssh;

import com.nook.biz.node.framework.ssh.dto.MinaSshExecResult;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * SSH 命令通道: 跑命令 / 流式跑命令 / 上传文本.
 *
 * @author nook
 */
public interface SshChannel {

    /**
     * 跑命令; 默认超时取 cred.sshOpTimeoutSeconds (跨地区可调).
     *
     * @param cmd 远端 shell 命令
     * @return SshExecResult
     */
    MinaSshExecResult exec(String cmd);

    /**
     * 跑命令 (显式 timeout); 仅在跟通用 op timeout 语义不同的场景用 (如 5s 探活).
     *
     * @param cmd     远端 shell 命令
     * @param timeout 超时
     * @return SshExecResult
     */
    MinaSshExecResult exec(String cmd, Duration timeout);

    /**
     * 流式跑命令; 每读到一行 stdout 回调 lineConsumer; stderr 不走回调, 调用方需手动 2>&1.
     *
     * @param cmd          远端 shell 命令
     * @param timeout      超时 (流式场景超时差异大, 必须显式)
     * @param lineConsumer 每行 stdout 的消费回调
     * @return SshExecResult (stdout 留空, 已被 lineConsumer 实时消费)
     */
    MinaSshExecResult execStream(String cmd, Duration timeout, Consumer<String> lineConsumer);

    /**
     * 把字符串写到远端路径并 chmod 600; 默认超时取 cred.sshUploadTimeoutSeconds.
     *
     * @param remotePath 远端绝对路径
     * @param content    文件内容
     */
    void uploadString(String remotePath, String content);

    /**
     * 把字符串写到远端路径并 chmod 600 (显式 timeout).
     *
     * @param remotePath 远端绝对路径
     * @param content    文件内容
     * @param timeout    超时
     */
    void uploadString(String remotePath, String content, Duration timeout);
}
