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

    private String host;

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
