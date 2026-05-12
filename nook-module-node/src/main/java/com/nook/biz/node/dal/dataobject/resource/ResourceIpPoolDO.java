package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IP 池条目 DO (一个出口 IP + SOCKS5 凭据).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_pool")
public class ResourceIpPoolDO extends BaseEntity {

    /** 区域 (us-west / jp / hk / sg 等) */
    private String region;

    /** 关联 resource_ip_type.id */
    private String ipTypeId;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    private Integer status;

    private String assignedMemberId;
    private LocalDateTime assignedAt;

    private LocalDateTime coolingUntil;

    private Integer assignCount;
    private LocalDateTime lastHealthAt;

    private String remark;
}
