package com.nook.biz.trade.controller;

import com.nook.biz.trade.controller.vo.SubscriptionCreateReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.convert.TradeSubscriptionConvert;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;

import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 订阅管理 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/trade/subscription")
@Validated
public class TradeSubscriptionController {

    @Resource
    private TradeSubscriptionService subscriptionService;

    /** 代客下单（管理端手动给会员分配套餐），自动选址并开通客户端 */
    @PostMapping("/create-sub")
    public Result<TradeSubscriptionRespVO> adminCreate(@Valid @RequestBody SubscriptionCreateReqVO reqVO) {
        TradeSubscriptionDO sub = subscriptionService.adminCreate(reqVO);
        Map<String, String> planNameMap = subscriptionService.getPlanNameMap(List.of(sub.getPlanId()));
        // 下单结果只回套餐名, 不展示会员邮箱, 故邮箱参数传 null
        return Result.ok(TradeSubscriptionConvert.INSTANCE.toRespVO(
                sub, planNameMap.get(sub.getPlanId()), null));
    }

    /** 订阅分页（附带套餐名、会员邮箱） */
    @GetMapping("/page-sub")
    public Result<PageResult<TradeSubscriptionRespVO>> getPage(@Valid TradeSubscriptionPageReqVO reqVO) {
        PageResult<TradeSubscriptionDO> page = subscriptionService.getPage(reqVO);
        List<TradeSubscriptionDO> records = page.getRecords();
        Map<String, String> planNameMap = subscriptionService.getPlanNameMap(
                CollectionUtils.convertSet(records, TradeSubscriptionDO::getPlanId));
        Map<String, String> memberEmailMap = subscriptionService.getMemberEmailMap(
                CollectionUtils.convertSet(records, TradeSubscriptionDO::getMemberUserId));
        return Result.ok(TradeSubscriptionConvert.INSTANCE.convertPage(page, planNameMap, memberEmailMap));
    }

    /** 统计各套餐当前"生效中"的订阅数（套餐编号 → 数量） */
    @GetMapping("/count-active-by-plan")
    public Result<Map<String, Integer>> countActiveByPlan() {
        return Result.ok(subscriptionService.countActiveByPlan());
    }

    /** 退订（注销该订阅的客户端、释放占用的落地机） */
    @PostMapping("/cancel-sub")
    public Result<Boolean> cancel(@RequestParam("id") String id) {
        subscriptionService.cancel(id);
        return Result.ok(true);
    }
}
