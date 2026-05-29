package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.convert.TradePlanConvert.PlanCapacity;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 套餐管理 Service 实现. 返回 DO; VO 转换由 controller + convert 层处理.
 *
 * <p>套餐 = 区域 + 规格 (不绑具体资源); 下单时 allocator 按区域 + IP 类型自动匹配落地机 + 线路机。
 * 容量 = 同区域 + 同 IP 类型 + 规格达标的可用落地机数。
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String OCCUPIED = "OCCUPIED";

    private final TradePlanMapper planMapper;
    private final TradePlanValidator planValidator;
    private final ResourceServerLandingApi landingApi;

    @Override
    public PageResult<TradePlanDO> getPlanPage(TradePlanPageReqVO req) {
        IPage<TradePlanDO> page = planMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getRegionCode(), req.getIpTypeId(), req.getEnabled(), req.getKeyword());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public TradePlanDO getPlan(String id) {
        return planValidator.validateExists(id);
    }

    @Override
    public Map<String, PlanCapacity> getCapacityMap(Collection<TradePlanDO> plans) {
        if (plans == null || plans.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PlanCapacity> map = new HashMap<>(plans.size());
        for (TradePlanDO plan : plans) {
            map.put(plan.getId(), capacityOf(plan));
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPlan(TradePlanSaveReqVO req) {
        planValidator.validateCodeUnique(req.getCode(), null);
        TradePlanDO e = TradePlanConvert.INSTANCE.toDO(req);
        e.setId(null);
        e.setEnabled(0);
        planMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(TradePlanSaveReqVO req) {
        TradePlanDO existing = planValidator.validateExists(req.getId());
        // 仅可变字段; 已售卖核心字段 (区域/IP类型/流量/带宽/周期/价格) 忽略 (改了破历史订阅)
        existing.setName(req.getName());
        existing.setRemark(req.getRemark());
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
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(String id) {
        planValidator.validateExists(id);
        planValidator.validateNoActiveSub(id);
        planMapper.deleteById(id);
    }

    /** 容量 = 同区域 + 同 IP 类型 + 规格达标的 LIVE 落地机 (按 status 分 available / occupied). */
    private PlanCapacity capacityOf(TradePlanDO plan) {
        if (plan.getRegionCode() == null || plan.getIpTypeId() == null) {
            return new PlanCapacity(0, 0, 0);
        }
        List<LandingSummaryDTO> matching = landingApi.findMatchingForPlan(
                plan.getRegionCode(), plan.getIpTypeId(),
                plan.getTrafficGb() == null ? 0 : plan.getTrafficGb(),
                plan.getBandwidthMbps() == null ? 0 : plan.getBandwidthMbps());
        int total = matching.size();
        int avail = (int) matching.stream().filter(l -> AVAILABLE.equals(l.getStatus())).count();
        int occ = (int) matching.stream().filter(l -> OCCUPIED.equals(l.getStatus())).count();
        return new PlanCapacity(total, avail, occ);
    }
}
