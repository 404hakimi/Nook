package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.AdminCreateSubReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 订阅管理 Service 接口
 *
 * @author nook
 */
public interface TradeSubscriptionService {

    /**
     * Admin 代客下单
     *
     * @param req 下单信息
     * @return 订阅
     */
    TradeSubscriptionDO adminCreate(AdminCreateSubReqVO req);

    /**
     * 获得订阅分页列表
     *
     * @param req 分页条件
     * @return 分页列表
     */
    PageResult<TradeSubscriptionDO> getPage(TradeSubscriptionPageReqVO req);

    /**
     * 批量查套餐名(key=套餐ID, value=套餐名)
     *
     * @param planIds 套餐ID集合
     * @return Map<String, String>
     */
    Map<String, String> getPlanNameMap(Collection<String> planIds);

    /**
     * 批量查会员邮箱(key=会员ID, value=邮箱)
     *
     * @param memberIds 会员ID集合
     * @return Map<String, String>
     */
    Map<String, String> getMemberEmailMap(Collection<String> memberIds);

    /**
     * 统计各套餐当前生效中订阅数(key=套餐ID, value=数量)
     *
     * @return Map<String, Integer>
     */
    Map<String, Integer> countActiveByPlan();

    /**
     * 退订
     *
     * @param id 订阅ID
     */
    void cancel(String id);

    /**
     * 渲染会员聚合订阅内容
     *
     * @param subToken 订阅 token
     * @return Base64 编码的订阅正文; token 无效返 null
     */
    String renderSubscription(String subToken);
}
