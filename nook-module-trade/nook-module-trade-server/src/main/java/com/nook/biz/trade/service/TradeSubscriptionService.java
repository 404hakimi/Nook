package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.AdminCreateSubReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 订阅管理 Service. 返回 DO; VO 转换由 controller + convert 层处理.
 *
 * @author nook
 */
public interface TradeSubscriptionService {

    /** admin 代客下单: allocator 选址 + 复用 provision 开通 + 建订阅, 返回订阅 DO. */
    TradeSubscriptionDO adminCreate(AdminCreateSubReqVO req);

    /** 订阅分页 (DO). */
    PageResult<TradeSubscriptionDO> getPage(TradeSubscriptionPageReqVO req);

    /** 批量查 planId → 套餐名; controller 组 VO 时 enrich 用. */
    Map<String, String> getPlanNameMap(Collection<String> planIds);

    /** 退订: 吊销 client + 落地机释放 + 状态 CANCELLED. */
    void cancel(String id);

    /**
     * 渲染会员聚合订阅内容 (Base64 vmess 列表). token 无效 / 会员禁用返 null (上层 → 404).
     *
     * @param subToken member_user.sub_token
     * @return Base64 编码的订阅正文; token 无效返 null
     */
    String renderSubscription(String subToken);
}
