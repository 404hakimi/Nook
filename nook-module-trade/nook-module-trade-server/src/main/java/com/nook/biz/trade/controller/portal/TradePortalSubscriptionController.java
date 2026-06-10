package com.nook.biz.trade.controller.portal;

import com.nook.biz.trade.controller.portal.vo.PortalSubscriptionRespVO;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpMemberUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户端 - 会员订阅 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/portal/member/subscriptions")
public class TradePortalSubscriptionController {

    @Resource
    private TradeSubscriptionService tradeSubscriptionService;

    /**
     * 当前登录会员的订阅 (含套餐 + 剩余额度)
     *
     * @return 会员订阅套餐列表
     */
    @GetMapping
    public Result<List<PortalSubscriptionRespVO>> loginMemberSubscriptions() {
        String memberUserId = StpMemberUtil.getLoginIdAsString();
        return Result.ok(tradeSubscriptionService.listMemberSubscriptions(memberUserId));
    }
}
