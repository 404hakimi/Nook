package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线路机扩展 DO
 *
 * @author nook
 */
@Data
@TableName("resource_server_frontline")
public class ResourceServerFrontlineDO {

    @TableId
    private String serverId;

    private String domain;

    private String cfZoneId;

    private String cfRecordId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
