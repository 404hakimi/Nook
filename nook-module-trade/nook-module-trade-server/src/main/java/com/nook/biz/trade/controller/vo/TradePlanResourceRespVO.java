package com.nook.biz.trade.controller.vo;

import lombok.Data;

/**
 * 套餐关联资源 Response (含 enrich: server 名 / IP / 状态).
 *
 * @author nook
 */
@Data
public class TradePlanResourceRespVO {

    /** trade_plan_resource.id (解绑用). */
    private String id;

    /** FRONTLINE / LANDING. */
    private String resourceType;

    /** resource_server.id. */
    private String resourceId;

    private Integer enabled;

    // ===== enrich =====
    private String name;

    private String ipAddress;

    /** 装机生命周期. */
    private String lifecycleState;

    /** 占用状态 (仅 landing). */
    private String landingStatus;
}
