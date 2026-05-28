package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.trade.api.enums.TradePlanResourceTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.controller.vo.BindResourceReqVO;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanResourceRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradePlanResourceDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanResourceMapper;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.biz.trade.validator.TradePlanResourceValidator;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 套餐管理 Service 实现.
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService {

    private static final String LIVE = "LIVE";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String OCCUPIED = "OCCUPIED";
    private static final String LANDING = TradePlanResourceTypeEnum.LANDING.getType();

    private final TradePlanMapper planMapper;
    private final TradePlanResourceMapper resourceMapper;
    private final TradePlanValidator planValidator;
    private final TradePlanResourceValidator resourceValidator;
    private final ResourceServerApi serverApi;
    private final ResourceServerLandingApi landingApi;

    @Override
    public PageResult<TradePlanRespVO> getPlanPage(TradePlanPageReqVO req) {
        IPage<TradePlanDO> page = planMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getRegionCode(), req.getIpTypeId(), req.getEnabled(), req.getKeyword());
        List<TradePlanRespVO> records = TradePlanConvert.INSTANCE.toRespVOList(page.getRecords());
        records.forEach(this::fillCapacity);
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public TradePlanRespVO getPlan(String id) {
        TradePlanDO e = planValidator.validateExists(id);
        TradePlanRespVO vo = TradePlanConvert.INSTANCE.toRespVO(e);
        fillCapacity(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPlan(TradePlanSaveReqVO req) {
        planValidator.validateCodeUnique(req.getCode(), null);
        TradePlanDO e = TradePlanConvert.INSTANCE.toDO(req);
        e.setId(null);
        e.setEnabled(0);
        if (e.getLimitIp() == null) {
            e.setLimitIp(0);
        }
        planMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(TradePlanSaveReqVO req) {
        TradePlanDO existing = planValidator.validateExists(req.getId());
        // 仅可变字段; 已售卖核心字段忽略 (改了破历史订阅)
        existing.setName(req.getName());
        existing.setBandwidthMbps(req.getBandwidthMbps());
        existing.setCostBasisCny(req.getCostBasisCny());
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
        resourceMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<TradePlanResourceDO>lambdaQuery()
                .eq(TradePlanResourceDO::getTradePlanId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindResource(BindResourceReqVO req) {
        TradePlanDO plan = planValidator.validateExists(req.getPlanId());
        resourceValidator.validateBind(plan, req.getResourceType(), req.getResourceId());
        TradePlanResourceDO e = new TradePlanResourceDO();
        e.setTradePlanId(req.getPlanId());
        e.setResourceType(req.getResourceType());
        e.setResourceId(req.getResourceId());
        e.setEnabled(1);
        resourceMapper.insert(e);
    }

    @Override
    public void unbindResource(String resourceRelId) {
        resourceMapper.deleteById(resourceRelId);
    }

    @Override
    public List<TradePlanResourceRespVO> listResource(String planId) {
        List<TradePlanResourceDO> list = resourceMapper.selectByPlan(planId, null);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ids = list.stream()
                .map(TradePlanResourceDO::getResourceId).collect(Collectors.toSet());
        Map<String, ResourceServerRespDTO> srvMap = serverApi.listByServerIds(ids).stream()
                .collect(Collectors.toMap(ResourceServerRespDTO::getId, Function.identity()));
        Set<String> landingIds = list.stream()
                .filter(r -> LANDING.equals(r.getResourceType()))
                .map(TradePlanResourceDO::getResourceId).collect(Collectors.toSet());
        Map<String, LandingSummaryDTO> landingMap = landingApi.listSummaryByServerIds(landingIds).stream()
                .collect(Collectors.toMap(LandingSummaryDTO::getServerId, Function.identity()));

        List<TradePlanResourceRespVO> result = new ArrayList<>(list.size());
        for (TradePlanResourceDO r : list) {
            TradePlanResourceRespVO vo = new TradePlanResourceRespVO();
            vo.setId(r.getId());
            vo.setResourceType(r.getResourceType());
            vo.setResourceId(r.getResourceId());
            vo.setEnabled(r.getEnabled());
            ResourceServerRespDTO srv = srvMap.get(r.getResourceId());
            if (srv != null) {
                vo.setName(srv.getName());
                vo.setIpAddress(srv.getIpAddress());
                vo.setLifecycleState(srv.getLifecycleState());
            }
            LandingSummaryDTO l = landingMap.get(r.getResourceId());
            if (l != null) {
                vo.setLandingStatus(l.getStatus());
            }
            result.add(vo);
        }
        return result;
    }

    /** 容量 = SKU 池里 enabled landing 中 LIVE 的数量 (按 status 分 available/occupied). */
    private void fillCapacity(TradePlanRespVO vo) {
        Set<String> landingIds = resourceMapper.selectByPlan(vo.getId(), LANDING).stream()
                .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                .map(TradePlanResourceDO::getResourceId)
                .collect(Collectors.toSet());
        int total = 0;
        int available = 0;
        int occupied = 0;
        if (!landingIds.isEmpty()) {
            for (LandingSummaryDTO s : landingApi.listSummaryByServerIds(landingIds)) {
                if (!LIVE.equals(s.getLifecycleState())) {
                    continue;
                }
                total++;
                if (AVAILABLE.equals(s.getStatus())) {
                    available++;
                } else if (OCCUPIED.equals(s.getStatus())) {
                    occupied++;
                }
            }
        }
        vo.setCapacityTotal(total);
        vo.setCapacityAvailable(available);
        vo.setCapacityOccupied(occupied);
    }
}
