package com.nook.biz.trade.controller.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 管理后台 - 套餐分页查询 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradePlanPageReqVO extends PageParam {

    /** 区域码集合 (按城市选单个, 按国家选该国全部城市). */
    private List<String> regionCodes;

    /** IP 类型. */
    private String ipTypeId;

    /** 上下架过滤: 1=上架 0=下架; null=全部. */
    private Integer enabled;

    /** 关键词 (code / name 模糊). */
    private String keyword;
}
