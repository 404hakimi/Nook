package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.BindResourceReqVO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanResourceRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.common.web.response.PageResult;

import java.util.List;

/**
 * 套餐管理 Service
 *
 * @author nook
 */
public interface TradePlanService {

    /** 分页 (含 SKU 池容量). */
    PageResult<TradePlanRespVO> getPlanPage(TradePlanPageReqVO req);

    /** 详情 (含容量). */
    TradePlanRespVO getPlan(String id);

    /** 创建 (默认下架 enabled=0). */
    String createPlan(TradePlanSaveReqVO req);

    /** 更新 (仅可变字段: name/bandwidthMbps/costBasisCny/remark). */
    void updatePlan(TradePlanSaveReqVO req);

    /** 上/下架. */
    void toggleEnabled(String id, boolean enabled);

    /** 软删 (有活跃订阅时拒). */
    void deletePlan(String id);

    /** 绑定资源 (frontline / landing). */
    void bindResource(BindResourceReqVO req);

    /** 解绑资源 (by trade_plan_resource.id). */
    void unbindResource(String resourceRelId);

    /** 列出套餐关联资源 (含 enrich). */
    List<TradePlanResourceRespVO> listResource(String planId);
}
