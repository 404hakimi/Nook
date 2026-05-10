package com.nook.biz.resource.controller.ip.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * IP 池新增 / 编辑统一入参; Create 必填校验 + 全字段范围校验由 ResourceIpPoolValidator 完成.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSaveReqVO {

    private String region;

    private String ipTypeId;

    private String ipAddress;

    private String socks5Host;

    private Integer socks5Port;

    private String socks5Username;

    /** 编辑时留空 = 保持原值. */
    private String socks5Password;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded. */
    private Integer status;

    /** 综合评分 0-100, 越高优先派发. */
    private BigDecimal score;

    private Integer scamalyticsScore;

    private Integer ipqsScore;

    private String remark;
}
