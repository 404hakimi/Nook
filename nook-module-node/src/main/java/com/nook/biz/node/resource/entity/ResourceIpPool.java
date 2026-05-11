package com.nook.biz.node.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * SOCKS5 落地节点 = 一个出口 IP.
 * 业务流程见 sql/02_resource.sql 表注释和 docs/产品化与实现计划.md.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_pool")
public class ResourceIpPool extends BaseEntity {

    /** 区域: us-west / jp / hk / sg / ... */
    private String region;

    /** 关联 resource_ip_type.id */
    private String ipTypeId;

    /** 部署模式: 1=self_deploy (SSH+SOCKS5, 入库后可一键部署) 2=external (仅 SOCKS5, 第三方现成) */
    private Integer provisionMode;

    /** 出网真实 IP,*/
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
    private Integer status;

    private String assignedMemberId;
    private LocalDateTime assignedAt;

    private LocalDateTime coolingUntil;

    private Integer assignCount;
    private LocalDateTime lastHealthAt;

    private String remark;
}
