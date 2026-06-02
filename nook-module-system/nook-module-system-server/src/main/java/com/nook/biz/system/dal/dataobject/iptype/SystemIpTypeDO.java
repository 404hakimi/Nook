package com.nook.biz.system.dal.dataobject.iptype;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * IP 类型 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_ip_type")
public class SystemIpTypeDO extends BaseEntity {

    /** 类型编码: isp / datacenter / residential */
    private String code;

    /** 展示名称 */
    private String name;

    private String description;

    private Integer sortOrder;
}
