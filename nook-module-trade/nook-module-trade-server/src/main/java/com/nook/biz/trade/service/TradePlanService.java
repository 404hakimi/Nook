package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.TradePlanCreateReqVO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanUpdateReqVO;
import com.nook.common.web.response.PageResult;

/**
 * 套餐管理 Service 接口
 *
 * @author nook
 */
public interface TradePlanService {

    /**
     * 获得套餐分页列表(含落地机池容量)
     *
     * @param req 分页条件
     * @return 分页列表
     */
    PageResult<TradePlanRespVO> getPlanPage(TradePlanPageReqVO req);

    /**
     * 获得套餐详情(含落地机池容量)
     *
     * @param id 套餐ID
     * @return 套餐
     */
    TradePlanRespVO getPlan(String id);

    /**
     * 创建套餐
     *
     * @param req 创建信息
     * @return 主键ID
     */
    String createPlan(TradePlanCreateReqVO req);

    /**
     * 更新套餐
     *
     * @param req 更新信息
     */
    void updatePlan(TradePlanUpdateReqVO req);

    /**
     * 上/下架套餐
     *
     * @param id      套餐ID
     * @param enabled 是否上架
     */
    void toggleEnabled(String id, boolean enabled);

    /**
     * 删除套餐
     *
     * @param id 套餐ID
     */
    void deletePlan(String id);
}
