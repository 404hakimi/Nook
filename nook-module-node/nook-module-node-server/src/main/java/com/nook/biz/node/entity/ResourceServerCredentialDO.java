package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器 SSH 凭据 DO
 *
 * @author nook
 */
@Data
@TableName("resource_server_credential")
public class ResourceServerCredentialDO {

    /** 落地/线路机 id (主键). */
    @TableId
    private String serverId;

    /** SSH 端口. */
    private Integer sshPort;

    /** SSH 登录用户名. */
    private String sshUser;

    /** SSH 登录密码 (明文). */
    private String sshPassword;

    /** SSH 握手超时秒. */
    private Integer sshTimeoutSeconds;

    /** 单条命令执行超时秒. */
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时秒. */
    private Integer sshUploadTimeoutSeconds;

    /** 装机整体超时秒. */
    private Integer installTimeoutSeconds;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
