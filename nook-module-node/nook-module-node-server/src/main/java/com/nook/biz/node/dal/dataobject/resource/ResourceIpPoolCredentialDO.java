package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池 SSH 凭据 DO (1:1, provision_mode=1 用于一键部署 dante)
 *
 * @author nook
 */
@Data
@TableName("resource_ip_pool_credential")
public class ResourceIpPoolCredentialDO {

    @TableId
    private String ipId;

    /** SSH 主机; 留空 = ip_address 兜底. */
    private String sshHost;

    /** SSH 端口; 留空 = 22. */
    private Integer sshPort;

    /** SSH 用户; 留空 = root. */
    private String sshUser;

    /** SSH 密码明文 (后台受信网络). */
    private String sshPassword;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
