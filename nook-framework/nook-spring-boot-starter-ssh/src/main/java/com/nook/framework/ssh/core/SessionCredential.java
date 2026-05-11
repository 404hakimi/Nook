package com.nook.framework.ssh.core;

import lombok.Builder;
import lombok.Getter;

/**
 * SSH 凭据值对象, framework 内部传递; 业务侧在边界做一次转换后传入.
 *
 * <p>{@code serverId} 仅作 cache 键 / 日志识别符, 不绑定业务实体语义, ad-hoc 场景填 {@code "ad-hoc:host"} 即可.
 *
 * @author nook
 */
@Getter
@Builder
public class SessionCredential {

    /** 缓存键 / 日志识别符. */
    private final String serverId;

    private final String sshHost;
    private final int sshPort;
    private final String sshUser;
    private final String sshPassword;

    /** SSH 会话握手超时(秒). */
    private final int sshTimeoutSeconds;

    /** SSH 单条命令最大耗时(秒). */
    private final int sshOpTimeoutSeconds;

    /** SCP 上传单文件超时(秒). */
    private final int sshUploadTimeoutSeconds;

    /** 一次安装脚本最大耗时(秒); framework 不消费, 仅给业务侧读. */
    private final int installTimeoutSeconds;
}
