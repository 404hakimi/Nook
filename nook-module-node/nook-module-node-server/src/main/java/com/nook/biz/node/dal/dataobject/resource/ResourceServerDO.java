package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 服务器资源 DO (硬件 + SSH 凭据 + 装机生命周期 + 账面字段).
 * 容量监控字段拆到 {@link ResourceServerCapacityDO} (1:1); 高频运行时状态拆到 {@link ResourceServerRuntimeDO} (1:1).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServerDO extends BaseEntity {

    private String name;

    private String host;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private Integer sshTimeoutSeconds;

    private Integer sshOpTimeoutSeconds;

    private Integer sshUploadTimeoutSeconds;

    private Integer installTimeoutSeconds;

    /** 运营商承诺峰值带宽 (Mbps); 仅账面展示, 不参与 enforce. */
    private Integer bandwidthMbps;

    /** 线路机域名 (e.g., jp-01.nook.com); 用作用户连接的 host, LIVE 前置必填. */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare DNS record ID (server 域名的 A 记录). */
    private String cfRecordId;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    /** 账单日 (1-28); 月度流量重置参考. */
    private Integer billingCycleDay;

    /** 服务器到期日 (机房续费). */
    private LocalDate expiresAt;

    /** allocator 硬上限: 1C1G=50-100, 2C2G=200, 4C4G=500, 8C8G=1000. */
    private Integer maxConcurrentClients;

    /** 装机生命周期; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    private Integer totalIpCount;

    private String idcProvider;

    /** 区域码; FK → resource_region.code. */
    private String region;

    private String remark;

    @TableLogic
    private Integer deleted;

    /** Agent 鉴权 token; 装机时生成, agent push 接口校验 X-Agent-Token 必须等于这个值. */
    private String agentToken;
}
