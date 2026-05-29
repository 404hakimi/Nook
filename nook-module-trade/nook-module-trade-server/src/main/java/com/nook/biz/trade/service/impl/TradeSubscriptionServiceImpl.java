package com.nook.biz.trade.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.member.api.MemberUserApi;
import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;
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
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final int DEFAULT_PORT = 443;
    private static final DateTimeFormatter EXPIRE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final TradeSubscriptionMapper subMapper;
    private final TradePlanMapper planMapper;
    private final TradePlanValidator planValidator;
    private final TradeAllocator allocator;
    private final XrayClientProvisionApi provisionApi;
    private final XrayClientNodeApi clientNodeApi;
    private final MemberUserApi memberUserApi;
    private final ObjectMapper objectMapper;

    @Override
    public TradeSubscriptionRespVO adminCreate(AdminCreateSubReqVO req) {
        TradePlanDO plan = planValidator.validateEnabled(req.getPlanId());
        int planBw = plan.getBandwidthMbps() == null ? 0 : plan.getBandwidthMbps();
        int planTraffic = plan.getTrafficGb() == null ? 0 : plan.getTrafficGb();
        String frontlineId = allocator.pickFrontline(plan.getRegionCode(), planBw);
        if (frontlineId == null) {
            throw new BusinessException(TradeErrorCode.NO_AVAILABLE_FRONTLINE, plan.getRegionCode());
        }
        long now = System.currentTimeMillis();
        long expiry = now + (long) plan.getPeriodDays() * DAY_MS;
        Set<String> tried = new HashSet<>();
        while (true) {
            String landingId = allocator.pickLanding(
                    plan.getRegionCode(), plan.getIpTypeId(), planTraffic, planBw, tried);
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

    @Override
    public String renderSubscription(String subToken) {
        MemberSubscriberDTO member = memberUserApi.getActiveBySubToken(subToken);
        if (member == null) {
            return null;
        }
        List<TradeSubscriptionDO> subs = subMapper.selectActiveByMember(member.getId());
        if (subs.isEmpty()) {
            return Base64.encode("");
        }
        Map<String, TradeSubscriptionDO> subByClient = subs.stream()
                .collect(Collectors.toMap(TradeSubscriptionDO::getXrayClientId, s -> s, (a, b) -> a));
        List<XrayClientNodeDTO> nodes = clientNodeApi.getNodeInfos(subByClient.keySet());
        if (nodes.isEmpty()) {
            return Base64.encode("");
        }
        Set<String> planIds = subs.stream()
                .map(TradeSubscriptionDO::getPlanId).collect(Collectors.toSet());
        Map<String, String> planNameMap = planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TradePlanDO::getId, TradePlanDO::getName));

        StringBuilder lines = new StringBuilder();
        for (XrayClientNodeDTO node : nodes) {
            TradeSubscriptionDO sub = subByClient.get(node.getClientId());
            if (sub == null) {
                continue;
            }
            lines.append(buildVmessLink(node, sub, planNameMap.get(sub.getPlanId()))).append('\n');
        }
        return Base64.encode(lines.toString().trim());
    }

    /** 拼单个 vmess:// 链接 (host = 线路机固定域名 / 出网 IP). */
    private String buildVmessLink(XrayClientNodeDTO node, TradeSubscriptionDO sub, String planName) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("v", "2");
        v.put("ps", buildRemark(sub, planName));
        v.put("add", node.getHost());
        v.put("port", String.valueOf(node.getPort() == null ? DEFAULT_PORT : node.getPort()));
        v.put("id", node.getClientUuid());
        v.put("aid", "0");
        v.put("scy", "auto");
        v.put("net", StrUtil.blankToDefault(node.getTransport(), "ws"));
        v.put("type", "none");
        v.put("host", node.getHost());
        v.put("path", StrUtil.nullToEmpty(node.getWsPath()));
        v.put("tls", node.isTls() ? "tls" : "");
        if (node.isTls()) {
            v.put("sni", node.getHost());
        }
        return "vmess://" + Base64.encode(toJson(v));
    }

    /** 节点备注: "套餐名 | 到期 MM-dd". */
    private String buildRemark(TradeSubscriptionDO sub, String planName) {
        String name = StrUtil.blankToDefault(planName, "节点");
        if (sub.getExpiresAt() != null) {
            return name + " | 到期 " + sub.getExpiresAt().format(EXPIRE_FMT);
        }
        return name;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("vmess JSON 序列化失败", e);
        }
    }

    private XrayClientProvisionDTO buildProvisionDTO(AdminCreateSubReqVO req, TradePlanDO plan,
                                                     String frontlineId, String landingId, long expiry) {
        XrayClientProvisionDTO dto = new XrayClientProvisionDTO();
        dto.setServerId(frontlineId);
        dto.setIpId(landingId);
        dto.setMemberUserId(req.getMemberUserId());
        dto.setTotalBytes(plan.getTrafficGb() == null ? 0L : (long) plan.getTrafficGb() * GB);
        dto.setExpiryEpochMillis(expiry);
        dto.setLimitIp(0);
        dto.setBandwidthMbps(plan.getBandwidthMbps() == null ? 0 : plan.getBandwidthMbps());
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
