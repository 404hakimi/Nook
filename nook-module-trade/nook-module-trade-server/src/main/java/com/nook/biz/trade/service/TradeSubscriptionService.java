package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.AdminCreateSubReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.common.web.response.PageResult;

/**
 * 订阅管理 Service
 *
 * @author nook
 */
public interface TradeSubscriptionService {

    /** admin 代客下单: allocator 选址 + 复用 provision 开通 + 建订阅. */
    TradeSubscriptionRespVO adminCreate(AdminCreateSubReqVO req);

    /** 订阅分页 (含套餐名). */
    PageResult<TradeSubscriptionRespVO> getPage(TradeSubscriptionPageReqVO req);

    /** 退订: 吊销 client + 落地机释放 + 状态 CANCELLED. */
    void cancel(String id);
}
