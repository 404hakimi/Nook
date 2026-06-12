package com.nook.biz.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统域名 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_domain")
public class SystemDomainDO extends BaseEntity {

    /** 根域名 (一级域名, 如 karsu.cc). */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare API Token (DNS-01 签发 / 续期). */
    private String cfApiToken;

    /** 备注. */
    private String remark;
}
