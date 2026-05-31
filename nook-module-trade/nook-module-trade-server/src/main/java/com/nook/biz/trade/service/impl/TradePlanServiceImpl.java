package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.trade.api.enums.TradePlanEnabledEnum;
import com.nook.biz.trade.controller.vo.TradePlanCreateReqVO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanUpdateReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 套餐管理 Service 实现类
 *
 * @author nook
 */
@Service
public class TradePlanServiceImpl implements TradePlanService {

    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private TradePlanValidator planValidator;
    @Resource
    private ResourceServerLandingApi landingApi;
    @Resource
    private TradeSubscriptionMapper subMapper;

    @Override
    public PageResult<TradePlanRespVO> getPlanPage(TradePlanPageReqVO req) {
        IPage<TradePlanDO> page = planMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getRegionCodes(), req.getIpTypeId(), req.getEnabled(), req.getKeyword());
        PageResult<TradePlanDO> plans = PageResult.of(page.getTotal(), page.getRecords());
        // 容量(可售/已售)不在套餐表上, 落地机池属 node 域: 按套餐规格跨模块批量算, 再拼进 VO
        List<PlanSpecDTO> specs = TradePlanConvert.INSTANCE.toSpecs(plans.getRecords());
        Map<String, PlanCapacityDTO> capMap = landingApi.countCapacityForPlans(specs);
        Map<String, Integer> subCountMap = subMapper.countActiveByPlanIds(
                CollectionUtils.convertSet(plans.getRecords(), TradePlanDO::getId));
        return TradePlanConvert.INSTANCE.convertPage(plans, capMap, subCountMap);
    }

    @Override
    public TradePlanRespVO getPlan(String id) {
        TradePlanDO plan = planValidator.validateExists(id);
        // 单个套餐复用批量容量接口 (传单元素), 再按 planId 取回
        List<PlanSpecDTO> specs = TradePlanConvert.INSTANCE.toSpecs(List.of(plan));
        Map<String, PlanCapacityDTO> capMap = landingApi.countCapacityForPlans(specs);
        return TradePlanConvert.INSTANCE.toRespVO(plan, capMap.get(plan.getId()));
    }

    @Override
    public String createPlan(TradePlanCreateReqVO req) {
        planValidator.validateCodeUnique(req.getCode(), null);
        TradePlanDO e = TradePlanConvert.INSTANCE.toDO(req);
        // 新建一律先下架, admin 确认后手动上架才可被下单
        e.setEnabled(TradePlanEnabledEnum.DISABLED.getCode());
        planMapper.insert(e);
        return e.getId();
    }

    @Override
    public void updatePlan(TradePlanUpdateReqVO req) {
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
        patch.setEnabled(enabled ? TradePlanEnabledEnum.ENABLED.getCode() : TradePlanEnabledEnum.DISABLED.getCode());
        planMapper.updateById(patch);
    }

    @Override
    public void deletePlan(String id) {
        planValidator.validateExists(id);
        planValidator.validateNoActiveSub(id);
        planMapper.deleteById(id);
    }

    @Override
    public Map<String, Long> countByRegion() {
        return planMapper.countGroupByRegion();
    }
}
