package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 套餐定义 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_plan")
public class TradePlanDO extends BaseEntity {

    /** 套餐码 (唯一). */
    private String code;

    private String name;

    /** 产品区域; 匹配落地机/线路机 (FK → system_region.code). */
    private String regionCode;

    /** 产品 IP 类型; 匹配落地机 (FK → system_ip_type.id). */
    private String ipTypeId;

    /** 月配额 GB; 写 xray client totalBytes. */
    private Integer trafficGb;

    /** 账面带宽 Mbps; 真实生效 (落地机 dante 限速 + 线路机带宽准入). */
    private Integer bandwidthMbps;

    private Integer periodDays;

    /** 售价 CNY. */
    private BigDecimal price;

    /** 上下架: 1=上架 0=下架. */
    private Integer enabled;

    private String remark;

    @TableLogic
    private Integer deleted;
}
