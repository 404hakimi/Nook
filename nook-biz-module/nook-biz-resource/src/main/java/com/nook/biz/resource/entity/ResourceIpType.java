package com.nook.biz.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IP 类型(ISP / 机房 / 家宽)；初始数据由 99_seed.sql 提供，运营一般不动。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_ip_type")
public class ResourceIpType extends BaseEntity {

    /** 类型编码: isp / datacenter / residential */
    private String code;

    /** 展示名称 */
    private String name;

    private String description;

    private Integer sortOrder;

    /** 该类型 IP 退订后冷却分钟数 (家宽 IP 一般需要更长冷却以避免被识别为同一用户)。 */
    private Integer coolingMinutes;
}
