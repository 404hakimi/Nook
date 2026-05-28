package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 套餐资源池关联 DO (SKU ↔ server / landing)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_plan_resource")
public class TradePlanResourceDO extends BaseEntity {

    /** FK → trade_plan.id. */
    private String tradePlanId;

    /** FRONTLINE / LANDING; {@link com.nook.biz.trade.api.enums.TradePlanResourceTypeEnum}. */
    private String resourceType;

    /** FK → resource_server.id. */
    private String resourceId;

    /** 临时禁用: 1=启用 0=禁用. */
    private Integer enabled;
}
