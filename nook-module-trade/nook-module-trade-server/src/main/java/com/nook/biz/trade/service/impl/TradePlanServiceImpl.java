package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 套餐管理 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService {

    private final TradePlanMapper planMapper;
    private final TradePlanValidator planValidator;
    private final ResourceServerLandingApi landingApi;

    @Override
    public PlanPage getPlanPage(TradePlanPageReqVO req) {
        IPage<TradePlanDO> page = planMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getRegionCode(), req.getIpTypeId(), req.getEnabled(), req.getKeyword());
        PageResult<TradePlanDO> result = PageResult.of(page.getTotal(), page.getRecords());
        // 跨模块: 按套餐规格批量算落地机池容量 (planId → 容量), 编排在本层完成
        Map<String, PlanCapacityDTO> capMap = landingApi.countCapacityForPlans(
                TradePlanConvert.INSTANCE.toSpecs(result.getRecords()));
        return new PlanPage(result, capMap);
    }

    @Override
    public PlanDetail getPlan(String id) {
        TradePlanDO plan = planValidator.validateExists(id);
        Map<String, PlanCapacityDTO> capMap = landingApi.countCapacityForPlans(
                TradePlanConvert.INSTANCE.toSpecs(List.of(plan)));
        return new PlanDetail(plan, capMap.get(plan.getId()));
    }

    @Override
    public String createPlan(TradePlanSaveReqVO req) {
        planValidator.validateCodeUnique(req.getCode(), null);
        TradePlanDO e = TradePlanConvert.INSTANCE.toDO(req);
        e.setId(null);
        e.setEnabled(0);
        planMapper.insert(e);
        return e.getId();
    }

    @Override
    public void updatePlan(TradePlanSaveReqVO req) {
        TradePlanDO existing = planValidator.validateExists(req.getId());
        // 可改: 名称/备注/售价/周期 (售价仅展示, 周期仅影响之后下单, 都不动在用订阅)
        // 锁定: 区域/IP类型/流量/带宽/套餐码 (改了破在用订阅: 配额/限速/选址/容量统计)
        existing.setName(req.getName());
        existing.setRemark(req.getRemark());
        existing.setPrice(req.getPrice());
        existing.setPeriodDays(req.getPeriodDays());
        planMapper.updateById(existing);
    }

    @Override
    public void toggleEnabled(String id, boolean enabled) {
        planValidator.validateExists(id);
        TradePlanDO patch = new TradePlanDO();
        patch.setId(id);
        patch.setEnabled(enabled ? 1 : 0);
        planMapper.updateById(patch);
    }

    @Override
    public void deletePlan(String id) {
        planValidator.validateExists(id);
        planValidator.validateNoActiveSub(id);
        planMapper.deleteById(id);
    }
}
