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

    /** 区域码; FK → system_region.code. */
    private String regionCode;

    /** FK → system_ip_type.id. */
    private String ipTypeId;

    /** 月配额 GB; 写 xray client totalBytes. */
    private Integer trafficGb;

    /** 账面带宽 Mbps; 仅商品页展示, 不 enforce. */
    private Integer bandwidthMbps;

    private Integer periodDays;

    /** 同时连接 IP 数; 写 xray limitIp; 0=不限. */
    private Integer limitIp;

    private BigDecimal priceCny;

    private BigDecimal costBasisCny;

    /** 上下架: 1=上架 0=下架. */
    private Integer enabled;

    private String remark;

    @TableLogic
    private Integer deleted;
}
