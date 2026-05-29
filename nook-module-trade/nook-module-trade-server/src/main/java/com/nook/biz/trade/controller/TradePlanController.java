package com.nook.biz.trade.controller;

import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.convert.TradePlanConvert.PlanCapacity;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 套餐管理 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/trade/plan")
@Validated
@RequiredArgsConstructor
public class TradePlanController {

    private final TradePlanService planService;

    /** 套餐分页 (含匹配落地机容量). */
    @GetMapping("/page-plan")
    public Result<PageResult<TradePlanRespVO>> getPage(@Valid TradePlanPageReqVO reqVO) {
        PageResult<TradePlanDO> page = planService.getPlanPage(reqVO);
        Map<String, PlanCapacity> capMap = planService.getCapacityMap(page.getRecords());
        return Result.ok(TradePlanConvert.INSTANCE.convertPage(page, capMap));
    }

    /** 套餐详情. */
    @GetMapping("/get-plan")
    public Result<TradePlanRespVO> getPlan(@RequestParam("id") String id) {
        TradePlanDO plan = planService.getPlan(id);
        Map<String, PlanCapacity> capMap = planService.getCapacityMap(List.of(plan));
        return Result.ok(TradePlanConvert.INSTANCE.toRespVO(plan, capMap.get(plan.getId())));
    }

    /** 创建套餐 (默认下架). */
    @PostMapping("/create-plan")
    public Result<String> createPlan(@Valid @RequestBody TradePlanSaveReqVO reqVO) {
        return Result.ok(planService.createPlan(reqVO));
    }

    /** 更新套餐 (仅可变字段). */
    @PutMapping("/update-plan")
    public Result<Boolean> updatePlan(@Valid @RequestBody TradePlanSaveReqVO reqVO) {
        planService.updatePlan(reqVO);
        return Result.ok(true);
    }

    /** 上/下架. */
    @PostMapping("/toggle-enabled")
    public Result<Boolean> toggleEnabled(@RequestParam("id") String id,
                                         @RequestParam("enabled") boolean enabled) {
        planService.toggleEnabled(id, enabled);
        return Result.ok(true);
    }

    /** 删除套餐 (有活跃订阅时拒). */
    @DeleteMapping("/delete-plan")
    public Result<Boolean> deletePlan(@RequestParam("id") String id) {
        planService.deletePlan(id);
        return Result.ok(true);
    }
}
