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

    /** 线路机 id (主键). */
    @TableId
    private String serverId;

    /** 线路机对外域名 (客户端接入入口). */
    private String domain;

    /** Cloudflare Zone ID; 维护该域名解析用. */
    private String cfZoneId;

    /** Cloudflare DNS 记录 ID; 指向本机的 A 记录. */
    private String cfRecordId;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
