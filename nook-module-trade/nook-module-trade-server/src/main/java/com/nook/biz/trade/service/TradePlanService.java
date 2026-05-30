package com.nook.biz.trade.service;

import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.common.web.response.PageResult;

import java.util.Map;

/**
 * 套餐管理 Service 接口
 *
 * @author nook
 */
public interface TradePlanService {

    /** 分页 + 落地机池容量 (跨模块算容量在本层完成, controller 只转 VO). */
    PlanPage getPlanPage(TradePlanPageReqVO req);

    /** 详情 + 落地机池容量. */
    PlanDetail getPlan(String id);

    /** 创建 (默认下架 enabled=0). */
    String createPlan(TradePlanSaveReqVO req);

    /** 更新 (仅可变字段: name/remark/price/periodDays). */
    void updatePlan(TradePlanSaveReqVO req);

    /** 上/下架. */
    void toggleEnabled(String id, boolean enabled);

    /** 软删 (有活跃订阅时拒). */
    void deletePlan(String id);

    /** 套餐分页 + 容量 (planId → total/available/occupied). */
    record PlanPage(PageResult<TradePlanDO> page, Map<String, PlanCapacityDTO> capMap) { }

    /** 单套餐 + 容量. */
    record PlanDetail(TradePlanDO plan, PlanCapacityDTO capacity) { }
}
