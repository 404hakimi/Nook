package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资源 IP 池 DO (主表, 身份 + lifecycle + 占用; SSH/账面/dante/runtime 拆 1:1 子表)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_pool")
public class ResourceIpPoolDO extends BaseEntity {

    /** 区域码; FK → resource_region.code. */
    private String region;

    /** 关联 resource_ip_type.id. */
    private String ipTypeId;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址. */
    private String ipAddress;

    /** 装机生命周期; 取值见 {@link ResourceIpPoolLifecycleEnum}. */
    private String lifecycleState;

    /** 占用状态; 取值见 {@link ResourceIpPoolStatusEnum}. */
    private String status;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum}. */
    private Integer provisionMode;

    /** status=OCCUPIED 时填占用会员 id. */
    private String occupiedByMemberId;

    private LocalDateTime occupiedAt;

    /** status=COOLING 时填冷却结束时间. */
    private LocalDateTime coolingUntil;

    /** status=RESERVED 时填超时时间. */
    private LocalDateTime reservedExpiresAt;

    /** Agent 鉴权 token; 装机一次性 SHA256 生成 (落地机也跑 nook-agent). */
    private String agentToken;

    private String remark;
}
