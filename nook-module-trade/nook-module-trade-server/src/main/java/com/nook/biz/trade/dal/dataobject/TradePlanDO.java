package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradePlanEnabledEnum;
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

    /** 套餐码, 全局唯一; 形如 jp_tyo_residential_100gb_monthly. */
    private String code;

    /** 套餐展示名. */
    private String name;

    /** 产品区域; 选址时匹配落地机/线路机. */
    private String regionCode;

    /** 产品 IP 类型; 选址时匹配落地机. */
    private String ipTypeId;

    /** 月流量配额 GB; 已用量 (member_plan_traffic) 达此值即停服. */
    private Integer trafficGb;

    /** 带宽上限 Mbps; 真实生效, 由占用落地机的本订阅派生 tc 限速值. */
    private Integer bandwidthMbps;

    /** 订阅周期天数; 下单按此推 expiresAt. */
    private Integer periodDays;

    /** 售价 CNY. */
    private BigDecimal price;

    /** 是否上架 {@link TradePlanEnabledEnum}; 仅上架套餐可被下单. */
    private Integer enabled;

    /** 备注. */
    private String remark;
}
