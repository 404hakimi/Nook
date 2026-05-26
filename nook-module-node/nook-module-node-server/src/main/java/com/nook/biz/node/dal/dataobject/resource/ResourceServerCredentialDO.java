package com.nook.biz.node.dal.dataobject.resource;

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

    @TableId
    private String serverId;

    /** SSH 端口; 主机 = resource_server.ip_address (canonical) */
    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private Integer sshTimeoutSeconds;

    private Integer sshOpTimeoutSeconds;

    private Integer sshUploadTimeoutSeconds;

    private Integer installTimeoutSeconds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
