package com.nook.biz.trade.api.enums;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trade 模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum TradeErrorCode implements ErrorCode {

    PLAN_NOT_FOUND(4001, "套餐 %s 不存在"),
    PLAN_CODE_DUPLICATE(4002, "套餐码 %s 已存在"),
    PLAN_IMMUTABLE_FIELD(4003, "套餐已上架字段不可改: %s (请建新套餐)"),
    PLAN_DISABLED(4004, "套餐 %s 已下架, 不可下单"),
    PLAN_HAS_ACTIVE_SUB(4005, "套餐 %s 仍有活跃订阅, 不可删除"),

    PLAN_RESOURCE_TYPE_MISMATCH(4011, "落地机区域/IP 类型与套餐已绑的不一致 (同一套餐落地机须同区域+同 IP 类型)"),
    PLAN_RESOURCE_NOT_LIVE(4012, "资源 %s 非 LIVE 状态, 不可关联"),
    PLAN_RESOURCE_DUPLICATE(4013, "资源已关联到该套餐"),
    PLAN_TRAFFIC_EXCEEDS_LANDING(4014, "套餐月流量 %sGB 超过落地机上限 %sGB"),
    PLAN_BANDWIDTH_EXCEEDS_LANDING(4015, "套餐带宽 %sMbps 超过落地机上限 %sMbps"),

    SKU_OUT_OF_STOCK(4021, "套餐 %s 无可分配落地机 (售罄)"),
    NO_AVAILABLE_FRONTLINE(4022, "套餐 %s 无可用线路机"),

    SUB_NOT_FOUND(4031, "订阅 %s 不存在"),
    SUB_NOT_ACTIVE(4032, "订阅 %s 非 ACTIVE 状态"),
    ;

    private final int code;
    private final String message;
}
