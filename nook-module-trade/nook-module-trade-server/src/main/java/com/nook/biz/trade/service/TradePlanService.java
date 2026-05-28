package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.common.web.response.PageResult;

/**
 * 套餐管理 Service
 *
 * @author nook
 */
public interface TradePlanService {

    /** 分页 (含匹配落地机容量). */
    PageResult<TradePlanRespVO> getPlanPage(TradePlanPageReqVO req);

    /** 详情 (含容量). */
    TradePlanRespVO getPlan(String id);

    /** 创建 (默认下架 enabled=0). */
    String createPlan(TradePlanSaveReqVO req);

    /** 更新 (仅可变字段: name/remark). */
    void updatePlan(TradePlanSaveReqVO req);

    /** 上/下架. */
    void toggleEnabled(String id, boolean enabled);

    /** 软删 (有活跃订阅时拒). */
    void deletePlan(String id);
}
