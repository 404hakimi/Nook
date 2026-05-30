package com.nook.biz.trade.service;

import com.nook.biz.trade.controller.vo.TradeSubscriptionChangeLogPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionChangeLogRespVO;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.common.web.response.PageResult;

import java.util.List;

/**
 * 订阅换机历史日志 Service 接口
 *
 * @author nook
 */
public interface TradeSubscriptionChangeLogService {

    /**
     * 记录一条换机日志 (由换机事件监听器调用)
     *
     * @param event 换机事件
     */
    void record(SubscriptionMachineChangeEvent event);

    /**
     * 换机历史分页 (按时间倒序, 附会员邮箱/机器出网 IP)
     *
     * @param req 分页条件
     * @return 分页列表
     */
    PageResult<TradeSubscriptionChangeLogRespVO> getPage(TradeSubscriptionChangeLogPageReqVO req);

    /**
     * 查某订阅的换机历史 (按时间倒序, 附会员邮箱/机器出网 IP)
     *
     * @param subscriptionId 订阅 id
     * @return 换机历史
     */
    List<TradeSubscriptionChangeLogRespVO> getBySubscription(String subscriptionId);
}
