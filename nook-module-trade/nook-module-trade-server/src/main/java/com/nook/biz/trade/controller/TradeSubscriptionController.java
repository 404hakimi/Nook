package com.nook.biz.trade.controller;

import com.nook.biz.trade.controller.vo.AdminCreateSubReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.convert.TradeSubscriptionConvert;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TradeSubscriptionController {

    private final TradeSubscriptionService subscriptionService;

    /** admin 代客下单 (allocator 选址 + 复用 provision 开通). */
    @PostMapping("/admin-create")
    public Result<TradeSubscriptionRespVO> adminCreate(@Valid @RequestBody AdminCreateSubReqVO reqVO) {
        TradeSubscriptionDO sub = subscriptionService.adminCreate(reqVO);
        Map<String, String> planNameMap = subscriptionService.getPlanNameMap(List.of(sub.getPlanId()));
        return Result.ok(TradeSubscriptionConvert.INSTANCE.toRespVO(sub, planNameMap.get(sub.getPlanId())));
    }

    /** 订阅分页. */
    @GetMapping("/page-sub")
    public Result<PageResult<TradeSubscriptionRespVO>> getPage(@Valid TradeSubscriptionPageReqVO reqVO) {
        PageResult<TradeSubscriptionDO> page = subscriptionService.getPage(reqVO);
        Map<String, String> planNameMap = subscriptionService.getPlanNameMap(
                TradeSubscriptionConvert.collectPlanIds(page.getRecords()));
        return Result.ok(TradeSubscriptionConvert.INSTANCE.convertPage(page, planNameMap));
    }

    /** 退订 (吊销 client + 落地机释放). */
    @PostMapping("/cancel")
    public Result<Boolean> cancel(@RequestParam("id") String id) {
        subscriptionService.cancel(id);
        return Result.ok(true);
    }
}
