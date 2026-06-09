package com.nook.biz.system.dal.dataobject.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统域名 DO (含 Cloudflare 配置; xray_install.domain_id 绑定)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_domain")
public class SystemDomainDO extends BaseEntity {

    /** 根域名 (一级域名, 如 karsu.cc); 二级域名存在 xray_install.subdomain, 共用此根域 + CF 配置. */
    private String domain;

    /** Cloudflare Zone ID. */
    private String cfZoneId;

    /** Cloudflare API Token (DNS-01 签发 / 续期). */
    private String cfApiToken;

    /** 备注. */
    private String remark;
}
