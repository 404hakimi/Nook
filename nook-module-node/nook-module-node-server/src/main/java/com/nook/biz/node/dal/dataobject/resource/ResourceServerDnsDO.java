package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器 DNS 绑定 DO
 *
 * @author nook
 */
@Data
@TableName("resource_server_dns")
public class ResourceServerDnsDO {

    @TableId
    private String serverId;

    private String domain;

    private String cfZoneId;

    private String cfRecordId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
