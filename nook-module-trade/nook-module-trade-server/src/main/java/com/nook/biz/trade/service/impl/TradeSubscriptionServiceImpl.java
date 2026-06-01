package com.nook.biz.trade.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.member.api.MemberUserApi;
import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.xray.XrayClientNodeApi;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;
import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.controller.vo.SubscriptionCreateReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.convert.TradeSubscriptionConvert;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.MemberPlanTrafficMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.service.TradeAllocator;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 订阅管理 Service 实现.
 *
 * @author nook
 */
@Slf4j
@Service
public class TradeSubscriptionServiceImpl implements TradeSubscriptionService {

    private static final long DAY_MS = 86_400_000L;
    private static final int DEFAULT_PORT = 443;
    private static final DateTimeFormatter EXPIRE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    /** 当前订阅只生成 vmess 链接; 其它协议待协议适配阶段放开 (node 侧已留 InboundProtocolMapping). */
    private static final String PROTOCOL_VMESS = "vmess";

    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private MemberPlanTrafficMapper trafficMapper;
    @Resource
    private TradePlanValidator planValidator;
    @Resource
    private TradeAllocator allocator;
    @Resource
    private XrayClientProvisionApi provisionApi;
    @Resource
    private XrayClientNodeApi clientNodeApi;
    @Resource
    private ResourceServerLandingApi landingApi;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public TradeSubscriptionDO adminCreate(SubscriptionCreateReqVO req) {
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

            // 仅"开通客户端"失败 (如落地机被并发抢占) 才换下一台落地机重试
            String clientId;
            try {
                clientId = provisionApi.provision(buildProvisionDTO(req, plan, frontlineId, landingId));
            } catch (BusinessException e) {
                log.warn("[adminCreate] provision 失败 landing={}, 试下一个: {}", landingId, e.getMessage());
                continue;
            }

            // 开通成功; 订阅落库失败不重试 (留下的孤儿客户端由对账兜底)
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
            // 发布开通分配事件 (监听器落换机历史日志, 与下单流程解耦)
            String operator = StpSystemUtil.getLoginIdOrSystem();
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), req.getMemberUserId(),
                    TradeSubscriptionChangeTypeEnum.FRONTLINE, null, frontlineId,
                    TradeSubscriptionChangeReasonEnum.OPEN, operator));
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), req.getMemberUserId(),
                    TradeSubscriptionChangeTypeEnum.LANDING, null, landingId,
                    TradeSubscriptionChangeReasonEnum.OPEN, operator));
            return sub;
        }
    }

    @Override
    public PageResult<TradeSubscriptionRespVO> getPage(TradeSubscriptionPageReqVO req) {
        IPage<TradeSubscriptionDO> page = subMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getMemberUserId(), req.getPlanId(), req.getStatus());
        List<TradeSubscriptionDO> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            return PageResult.of(page.getTotal(), Collections.emptyList());
        }
        // 套餐(取名 + 总流量配额): 同套餐多订阅, 批量查一次
        Map<String, TradePlanDO> planMap = CollectionUtils.convertMap(
                planMapper.selectBatchIds(CollectionUtils.convertSet(records, TradeSubscriptionDO::getPlanId)),
                TradePlanDO::getId);
        // 会员邮箱
        Map<String, String> emailMap = getMemberEmailMap(
                CollectionUtils.convertSet(records, TradeSubscriptionDO::getMemberUserId));
        // 已用流量(一份订阅一行; 尚未计量的订阅无行, 已用按 0)
        Map<String, MemberPlanTrafficDO> trafficMap = CollectionUtils.convertMap(
                trafficMapper.selectBatchIds(CollectionUtils.convertSet(records, TradeSubscriptionDO::getId)),
                MemberPlanTrafficDO::getSubscriptionId);
        // 客户端 → 所在线路机 / 占用落地机
        Set<String> clientIds = CollectionUtils.convertSet(records, TradeSubscriptionDO::getXrayClientId);
        Map<String, String> clientFrontlineMap = clientNodeApi.getServerIdByClientIds(clientIds);
        Map<String, String> clientLandingMap = clientNodeApi.getLandingIdByClientIds(clientIds);
        // 线路机 + 落地机出网 IP 合并一次查回
        Set<String> serverIds = new HashSet<>(clientFrontlineMap.values());
        serverIds.addAll(clientLandingMap.values());
        Map<String, String> serverIpMap = CollectionUtils.convertMap(
                landingApi.listSummaryByServerIds(serverIds),
                LandingSummaryDTO::getServerId, LandingSummaryDTO::getIpAddress);
        return TradeSubscriptionConvert.INSTANCE.convertPage(PageResult.of(page.getTotal(), records),
                planMap, emailMap, trafficMap, clientFrontlineMap, clientLandingMap, serverIpMap);
    }

    @Override
    public Map<String, String> getPlanNameMap(Collection<String> planIds) {
        if (CollectionUtils.isAnyEmpty(planIds)) {
            return Collections.emptyMap();
        }
        return CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId, TradePlanDO::getName);
    }

    @Override
    public Map<String, String> getMemberEmailMap(Collection<String> memberIds) {
        if (CollectionUtils.isAnyEmpty(memberIds)) {
            return Collections.emptyMap();
        }
        return memberUserApi.getEmailMap(memberIds);
    }

    @Override
    public Map<String, Integer> countActiveByPlan() {
        return subMapper.countActiveGroupByPlan();
    }

    @Override
    public void cancel(String id) {
        TradeSubscriptionDO sub = subMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(TradeErrorCode.SUB_NOT_FOUND, id);
        }
        // 吊销前先取占用的线路机/落地机, 退订后这些绑定会被释放
        Set<String> clientIds = Collections.singleton(sub.getXrayClientId());
        String frontlineId = clientNodeApi.getServerIdByClientIds(clientIds).get(sub.getXrayClientId());
        String landingId = clientNodeApi.getLandingIdByClientIds(clientIds).get(sub.getXrayClientId());
        provisionApi.revoke(sub.getXrayClientId());
        sub.setStatus(TradeSubscriptionStatusEnum.CANCELLED.getState());
        subMapper.updateById(sub);
        log.info("[cancel] sub={} client={} → CANCELLED", id, sub.getXrayClientId());
        // 发布退订释放事件 (监听器落换机历史日志, 与退订流程解耦)
        String operator = StpSystemUtil.getLoginIdOrSystem();
        if (frontlineId != null) {
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), sub.getMemberUserId(),
                    TradeSubscriptionChangeTypeEnum.FRONTLINE, frontlineId, null,
                    TradeSubscriptionChangeReasonEnum.RELEASE, operator));
        }
        if (landingId != null) {
            eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), sub.getMemberUserId(),
                    TradeSubscriptionChangeTypeEnum.LANDING, landingId, null,
                    TradeSubscriptionChangeReasonEnum.RELEASE, operator));
        }
    }

    @Override
    public String renderSubscription(String subToken) {
        // token 无效 / 会员被禁用 → 返 null, 上层转 404 (不暴露"token 存在但禁用"的区别)
        MemberSubscriberDTO member = memberUserApi.getActiveBySubToken(subToken);
        if (member == null) {
            return null;
        }
        // 会员名下全部 ACTIVE 订阅聚合成一份订阅; 无订阅返空串 (客户端导入得到空列表, 不报错)
        List<TradeSubscriptionDO> subs = subMapper.selectActiveByMember(member.getId());
        if (CollUtil.isEmpty(subs)) {
            return Base64.encode("");
        }
        // 按 clientId 索引, 后面用节点信息回查订阅 (一个 client 一份订阅, 撞 key 取先到的)
        Map<String, TradeSubscriptionDO> subByClient = CollectionUtils.convertMap(
                subs, TradeSubscriptionDO::getXrayClientId, Function.identity(), (a, b) -> a);
        // 节点按"运行中"且能拼出 host 过滤后返回; 全不可用 (如刚下单还没对账) 返空串
        List<XrayClientNodeDTO> nodes = clientNodeApi.getNodeInfos(subByClient.keySet());
        if (CollUtil.isEmpty(nodes)) {
            return Base64.encode("");
        }
        // 套餐名只用于节点备注展示, 批量查一次避免逐订阅查库
        Set<String> planIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId);
        Map<String, String> planNameMap = CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId, TradePlanDO::getName);

        StringBuilder lines = new StringBuilder();
        for (XrayClientNodeDTO node : nodes) {
            // getNodeInfos 可能比 subByClient 多 (并发场景), 回查不到的节点跳过
            TradeSubscriptionDO sub = subByClient.get(node.getClientId());
            if (sub == null) {
                continue;
            }
            // 目前只生成 vmess; 协议明确非 vmess 的节点跳过 (保留多协议扩展位), 不拼错链接; 协议空按 vmess 默认
            if (StrUtil.isNotBlank(node.getProtocol()) && !PROTOCOL_VMESS.equalsIgnoreCase(node.getProtocol())) {
                log.warn("[renderSubscription] 跳过非 vmess 节点: client={} protocol={}",
                        node.getClientId(), node.getProtocol());
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

    private XrayClientProvisionDTO buildProvisionDTO(SubscriptionCreateReqVO req, TradePlanDO plan,
                                                     String frontlineId, String landingId) {
        XrayClientProvisionDTO dto = new XrayClientProvisionDTO();
        dto.setServerId(frontlineId);
        dto.setIpId(landingId);
        dto.setMemberUserId(req.getMemberUserId());
        return dto;
    }

    private static LocalDateTime toLdt(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
