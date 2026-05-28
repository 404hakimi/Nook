package com.nook.biz.trade.controller.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 套餐分页查询入参.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradePlanPageReqVO extends PageParam {

    private String regionCode;

    private String ipTypeId;

    /** 上下架过滤: 1=上架 0=下架; null=全部. */
    private Integer enabled;

    /** 关键词 (code / name 模糊). */
    private String keyword;
}
