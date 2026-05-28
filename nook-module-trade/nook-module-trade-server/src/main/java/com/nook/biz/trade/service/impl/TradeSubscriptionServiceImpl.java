package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.controller.vo.AdminCreateSubReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.service.TradeAllocator;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订阅管理 Service 实现.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSubscriptionServiceImpl implements TradeSubscriptionService {

    private static final long GB = 1024L * 1024 * 1024;
    private static final long DAY_MS = 86_400_000L;

    private final TradeSubscriptionMapper subMapper;
    private final TradePlanMapper planMapper;
    private final TradePlanValidator planValidator;
    private final TradeAllocator allocator;
    private final XrayClientProvisionApi provisionApi;

    @Override
    public TradeSubscriptionRespVO adminCreate(AdminCreateSubReqVO req) {
        TradePlanDO plan = planValidator.validateEnabled(req.getPlanId());
        String frontlineId = allocator.pickFrontline(plan.getId());
        if (frontlineId == null) {
            throw new BusinessException(TradeErrorCode.NO_AVAILABLE_FRONTLINE, plan.getId());
        }
        long now = System.currentTimeMillis();
        long expiry = now + (long) plan.getPeriodDays() * DAY_MS;
        Set<String> tried = new HashSet<>();
        while (true) {
            String landingId = allocator.pickLanding(plan.getId(), tried);
            if (landingId == null) {
                throw new BusinessException(TradeErrorCode.SKU_OUT_OF_STOCK, plan.getId());
            }
            tried.add(landingId);

            // 仅 provision 失败 (如 landing 被并发抢占) 才换下一个落地机重试
            String clientId;
            try {
                clientId = provisionApi.provision(buildProvisionDTO(req, plan, frontlineId, landingId, expiry));
            } catch (BusinessException e) {
                log.warn("[adminCreate] provision 失败 landing={}, 试下一个: {}", landingId, e.getMessage());
                continue;
            }

            // provision 成功; sub 落库失败不重试 (留孤儿 client 由对账兜底)
            TradeSubscriptionDO sub = new TradeSubscriptionDO();
            sub.setMemberUserId(req.getMemberUserId());
            sub.setPlanId(plan.getId());
            sub.setXrayClientId(clientId);
            sub.setStartedAt(toLdt(now));
            sub.setExpiresAt(toLdt(expiry));
            sub.setStatus(TradeSubscriptionStatusEnum.ACTIVE.getState());
            subMapper.insert(sub);
            log.info("[adminCreate] OK member={} plan={} frontline={} landing={} client={}",
                    req.getMemberUserId(), plan.getId(), frontlineId, landingId, clientId);
            return toRespVO(sub, plan.getName());
        }
    }

    @Override
    public PageResult<TradeSubscriptionRespVO> getPage(TradeSubscriptionPageReqVO req) {
        IPage<TradeSubscriptionDO> page = subMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getMemberUserId(), req.getPlanId(), req.getStatus());
        if (page.getRecords().isEmpty()) {
            return PageResult.of(page.getTotal(), Collections.emptyList());
        }
        Set<String> planIds = page.getRecords().stream()
                .map(TradeSubscriptionDO::getPlanId).collect(Collectors.toSet());
        Map<String, String> planNameMap = planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TradePlanDO::getId, TradePlanDO::getName));
        List<TradeSubscriptionRespVO> records = page.getRecords().stream()
                .map(s -> toRespVO(s, planNameMap.get(s.getPlanId())))
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public void cancel(String id) {
        TradeSubscriptionDO sub = subMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(TradeErrorCode.SUB_NOT_FOUND, id);
        }
        provisionApi.revoke(sub.getXrayClientId());
        sub.setStatus(TradeSubscriptionStatusEnum.CANCELLED.getState());
        subMapper.updateById(sub);
        log.info("[cancel] sub={} client={} → CANCELLED", id, sub.getXrayClientId());
    }

    private XrayClientProvisionDTO buildProvisionDTO(AdminCreateSubReqVO req, TradePlanDO plan,
                                                     String frontlineId, String landingId, long expiry) {
        XrayClientProvisionDTO dto = new XrayClientProvisionDTO();
        dto.setServerId(frontlineId);
        dto.setIpId(landingId);
        dto.setMemberUserId(req.getMemberUserId());
        dto.setTotalBytes(plan.getTrafficGb() == null ? 0L : (long) plan.getTrafficGb() * GB);
        dto.setExpiryEpochMillis(expiry);
        dto.setLimitIp(plan.getLimitIp() == null ? 0 : plan.getLimitIp());
        return dto;
    }

    private TradeSubscriptionRespVO toRespVO(TradeSubscriptionDO sub, String planName) {
        TradeSubscriptionRespVO vo = new TradeSubscriptionRespVO();
        vo.setId(sub.getId());
        vo.setMemberUserId(sub.getMemberUserId());
        vo.setPlanId(sub.getPlanId());
        vo.setPlanName(planName);
        vo.setXrayClientId(sub.getXrayClientId());
        vo.setStartedAt(sub.getStartedAt());
        vo.setExpiresAt(sub.getExpiresAt());
        vo.setStatus(sub.getStatus());
        vo.setCreatedAt(sub.getCreatedAt());
        return vo;
    }

    private static LocalDateTime toLdt(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
