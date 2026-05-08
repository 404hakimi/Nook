package com.nook.biz.resource.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
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

    @TableLogic
    private Integer deleted;
}
