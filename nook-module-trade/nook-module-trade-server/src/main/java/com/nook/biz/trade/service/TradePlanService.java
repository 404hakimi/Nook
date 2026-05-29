package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.common.web.response.PageResult;

/**
 * 套餐管理 Service. 返回 DO; VO 转换由 controller + convert 层处理.
 *
 * @author nook
 */
public interface TradePlanService {

    /** 分页 (DO). */
    PageResult<TradePlanDO> getPlanPage(TradePlanPageReqVO req);

    /** 详情 (DO). */
    TradePlanDO getPlan(String id);

    /** 创建 (默认下架 enabled=0). */
    String createPlan(TradePlanSaveReqVO req);

    /** 更新 (仅可变字段: name/remark). */
    void updatePlan(TradePlanSaveReqVO req);

    /** 上/下架. */
    void toggleEnabled(String id, boolean enabled);

    /** 软删 (有活跃订阅时拒). */
    void deletePlan(String id);
}
