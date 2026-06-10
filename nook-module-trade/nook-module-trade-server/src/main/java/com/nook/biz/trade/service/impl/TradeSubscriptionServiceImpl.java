package com.nook.biz.trade.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.member.api.MemberUserApi;
import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.xray.XrayInboundApi;
import com.nook.biz.node.api.xray.dto.XrayInboundDTO;
import com.nook.biz.trade.api.enums.TradeCertSourceEnum;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.controller.admin.vo.SubscriptionCreateReqVO;
import com.nook.biz.trade.controller.admin.vo.TradeSubscriptionPageReqVO;
import com.nook.biz.trade.controller.portal.vo.PortalSubscriptionRespVO;
import com.nook.biz.trade.controller.admin.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.convert.TradeSubscriptionConvert;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionTrafficDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionTrafficMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.service.TradeAllocator;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.biz.trade.service.TradeSubscriptionService;
import com.nook.biz.trade.service.TradeSubscriptionQuotaService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.unit.TrafficUnitUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅管理 Service 实现.
 *
 * @author nook
 */
@Slf4j
@Service
public class TradeSubscriptionServiceImpl implements TradeSubscriptionService {

    private static final int DEFAULT_PORT = 443;
    private static final DateTimeFormatter EXPIRE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    /**
     * 当前订阅只生成 vmess 链接; 其它协议待协议适配阶段放开 (node 侧已留 InboundProtocolMapping).
     */
    private static final String PROTOCOL_VMESS = "vmess";
    /** Clash 隐藏总出口组名: MATCH 目标, 客户端选节点时同步切到对应套餐组. */
    private static final String CLASH_ROOT_GROUP = "Nook";
    /** 无可用节点时的 Clash 空配置. */
    private static final String EMPTY_CLASH = "proxies: []\n";

    @Resource
    private TradeSubscriptionMapper subMapper;
    @Resource
    private TradePlanMapper planMapper;
    @Resource
    private TradeSubscriptionTrafficMapper tradeSubscriptionTrafficMapper;
    @Resource
    private TradePlanValidator planValidator;
    @Resource
    private TradeAllocator allocator;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private TradeSubscriptionQuotaService tradeSubscriptionQuotaService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private XrayInboundApi xrayInboundApi;
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
        int planBw = ObjectUtil.isNull(plan.getBandwidthMbps()) ? 0 : plan.getBandwidthMbps();
        int planTraffic = ObjectUtil.isNull(plan.getTrafficGb()) ? 0 : plan.getTrafficGb();
        String frontlineId = allocator.pickFrontline(plan.getRegionCode(), planBw);
        if (ObjectUtil.isNull(frontlineId)) {
            throw new BusinessException(TradeErrorCode.NO_AVAILABLE_FRONTLINE, plan.getRegionCode());
        }
        // 候选落地机(同区域 + IP类型 + 规格达标 + 健康可分配)一次取全; 准入判定全在 node ResourceServerAdmission
        List<String> candidates = allocator.matchLandings(plan.getRegionCode(), plan.getIpTypeId(), planTraffic, planBw);
        if (CollUtil.isEmpty(candidates)) {
            throw new BusinessException(TradeErrorCode.SKU_OUT_OF_STOCK, plan.getId());
        }
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime expiresAt = startedAt.plusDays(plan.getPeriodDays());
        // 逐台试占, 一台一个事务: openOne 返 null = 落地机被并发抢占 → 换下一台; 其它异常直接抛(整笔回滚, 不掩盖真因)
        for (String landingId : candidates) {
            TradeSubscriptionDO sub;
            try {
                sub = transactionTemplate.execute(status ->
                        this.openOne(req, plan, frontlineId, landingId, planTraffic, startedAt, expiresAt));
            } catch (DuplicateKeyException e) {
                // 落地机被并发占 (cert.ip_id 撞 uk_cert_ip), 事务已回滚, 试下一台
                log.warn("[adminCreate] 落地机 {} 被并发抢占, 试下一台", landingId);
                continue;
            }
            this.publishOpenEvents(sub, req.getMemberUserId(), frontlineId, landingId);
            log.info("[adminCreate] OK member={} plan={} frontline={} landing={} sub={}",
                    req.getMemberUserId(), plan.getId(), frontlineId, landingId, sub.getId());
            return sub;
        }
        // 候选都被并发占完
        throw new BusinessException(TradeErrorCode.SKU_OUT_OF_STOCK, plan.getId());
    }

    /**
     * 一次开通的全部库内写入, 由调用方事务包裹: 签发凭证(带分配) → 建订阅 → 发基础额度; 落地机被并发占时 setAllocation 撞 uk_cert_ip 抛 DuplicateKeyException.
     */
    private TradeSubscriptionDO openOne(SubscriptionCreateReqVO req, TradePlanDO plan, String frontlineId,
                                        String landingId, int planTraffic,
                                        LocalDateTime startedAt, LocalDateTime expiresAt) {
        // 先生成订阅 id: 凭证按 subscription_id 反向关联它
        String subId = IdUtil.simpleUUID();
        TradeSubscriptionCertificateDO cert = tradeSubscriptionCertificateService.issue(
                subId, req.getMemberUserId(), TradeCertSourceEnum.BASE.getSource());
        // 占位即 claim: 写 ip_id 撞 uk_cert_ip 唯一键 = 被并发占 → DuplicateKeyException, 上层换下一台
        tradeSubscriptionCertificateService.setAllocation(cert.getId(), frontlineId, landingId);
        TradeSubscriptionDO sub = new TradeSubscriptionDO();
        sub.setId(subId);
        sub.setMemberUserId(req.getMemberUserId());
        sub.setPlanId(plan.getId());
        sub.setStartedAt(startedAt);
        sub.setExpiresAt(expiresAt);
        sub.setStatus(TradeSubscriptionStatusEnum.ACTIVE.getState());
        subMapper.insert(sub);
        // 基础额度作为一条额度账; 订阅有效额度 = 名下生效额度之和
        tradeSubscriptionQuotaService.createBaseQuota(subId, TrafficUnitUtils.gbToBytes(planTraffic), startedAt, expiresAt);
        return sub;
    }

    /**
     * 发布开通的换机历史事件 (与下单解耦, 事务外发).
     */
    private void publishOpenEvents(TradeSubscriptionDO sub, String memberUserId, String frontlineId, String landingId) {
        String operator = StpSystemUtil.getLoginIdOrSystem();
        eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), memberUserId,
                TradeSubscriptionChangeTypeEnum.FRONTLINE, null, frontlineId,
                TradeSubscriptionChangeReasonEnum.OPEN, operator));
        eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), memberUserId,
                TradeSubscriptionChangeTypeEnum.LANDING, null, landingId,
                TradeSubscriptionChangeReasonEnum.OPEN, operator));
    }

    @Override
    public PageResult<TradeSubscriptionRespVO> getPage(TradeSubscriptionPageReqVO req) {
        IPage<TradeSubscriptionDO> page = subMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getMemberUserId(), req.getPlanId(), req.getStatus());
        List<TradeSubscriptionDO> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            return PageResult.of(page.getTotal(), List.of());
        }
        // 套餐(取名 + 总流量配额): 同套餐多订阅, 批量查一次
        Map<String, TradePlanDO> planMap = CollectionUtils.convertMap(
                planMapper.selectBatchIds(CollectionUtils.convertSet(records, TradeSubscriptionDO::getPlanId)),
                TradePlanDO::getId);
        // 会员邮箱
        Map<String, String> emailMap = getMemberEmailMap(
                CollectionUtils.convertSet(records, TradeSubscriptionDO::getMemberUserId));
        // 订阅 → 凭证 → 所在线路机 / 占用落地机 (订阅维度)
        Set<String> subIds = CollectionUtils.convertSet(records, TradeSubscriptionDO::getId);
        // 本周期已用: 名下各接入点当周期 traffic 行 used_bytes 汇总到订阅 (无行按 0)
        Map<String, Long> usedBytesBySub = new HashMap<>();
        for (TradeSubscriptionTrafficDO row : tradeSubscriptionTrafficMapper.selectCurrentBySubscriptionIds(subIds)) {
            usedBytesBySub.merge(row.getSubscriptionId(),
                    ObjectUtil.isNull(row.getUsedBytes()) ? 0L : row.getUsedBytes(), Long::sum);
        }
        Map<String, String> subFrontlineMap = new HashMap<>();
        Map<String, String> subLandingMap = new HashMap<>();
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscriptionIds(subIds)) {
            if (ObjectUtil.isNotNull(cert.getServerId())) {
                subFrontlineMap.put(cert.getSubscriptionId(), cert.getServerId());
            }
            if (ObjectUtil.isNotNull(cert.getIpId())) {
                subLandingMap.put(cert.getSubscriptionId(), cert.getIpId());
            }
        }
        // 线路机 + 落地机出网 IP 合并一次查回
        Set<String> serverIds = new HashSet<>(subFrontlineMap.values());
        serverIds.addAll(subLandingMap.values());
        Map<String, String> serverIpMap = CollectionUtils.convertMap(
                landingApi.listSummaryByServerIds(serverIds),
                LandingSummaryDTO::getServerId, LandingSummaryDTO::getIpAddress);
        return TradeSubscriptionConvert.INSTANCE.convertPage(PageResult.of(page.getTotal(), records),
                planMap, emailMap, usedBytesBySub, subFrontlineMap, subLandingMap, serverIpMap);
    }

    @Override
    public Map<String, String> getPlanNameMap(Collection<String> planIds) {
        if (CollectionUtils.isAnyEmpty(planIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId, TradePlanDO::getName);
    }

    @Override
    public Map<String, String> getMemberEmailMap(Collection<String> memberIds) {
        if (CollectionUtils.isAnyEmpty(memberIds)) {
            return Map.of();
        }
        return memberUserApi.getEmailMap(memberIds);
    }

    @Override
    public Map<String, Integer> countActiveByPlan() {
        return subMapper.countActiveGroupByPlan();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id) {
        TradeSubscriptionDO sub = subMapper.selectById(id);
        if (ObjectUtil.isNull(sub)) {
            throw new BusinessException(TradeErrorCode.SUB_NOT_FOUND, id);
        }
        // 终态订阅 (已退订 / 已过期) 的凭证已吊销, 拦在前面
        if (TradeSubscriptionStatusEnum.CANCELLED.matches(sub.getStatus())
                || TradeSubscriptionStatusEnum.EXPIRED.matches(sub.getStatus())) {
            throw new BusinessException(TradeErrorCode.SUB_NOT_ACTIVE, id);
        }
        String operator = StpSystemUtil.getLoginIdOrSystem();
        // 名下凭证逐个: 释放落地机 + 置吊销; 远端 xray 由 agent 对账清理
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(id)) {
            String frontlineId = cert.getServerId();
            String landingId = cert.getIpId();
            // 释放: revoke 清 cert.ip_id (占用真相), 落地机随之空闲; 远端 xray 由 agent 对账清理
            tradeSubscriptionCertificateService.revoke(cert.getId());
            // 发布释放事件 (监听器落换机历史日志, 与退订解耦)
            if (ObjectUtil.isNotNull(frontlineId)) {
                eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), sub.getMemberUserId(),
                        TradeSubscriptionChangeTypeEnum.FRONTLINE, frontlineId, null,
                        TradeSubscriptionChangeReasonEnum.RELEASE, operator));
            }
            if (ObjectUtil.isNotNull(landingId)) {
                eventPublisher.publishEvent(new SubscriptionMachineChangeEvent(sub.getId(), sub.getMemberUserId(),
                        TradeSubscriptionChangeTypeEnum.LANDING, landingId, null,
                        TradeSubscriptionChangeReasonEnum.RELEASE, operator));
            }
        }
        sub.setStatus(TradeSubscriptionStatusEnum.CANCELLED.getState());
        subMapper.updateById(sub);
        log.info("[cancel] sub={} → CANCELLED", id);
    }

    @Override
    public List<PortalSubscriptionRespVO> listMemberSubscriptions(String memberUserId) {
        // 查询当前用户活跃的订阅
        List<TradeSubscriptionDO> subs = subMapper.selectActiveByMember(memberUserId);
        if (CollUtil.isEmpty(subs)) {
            return List.of();
        }
        // 根据订阅信息获取套餐
        List<TradePlanDO> planList = planMapper.selectBatchIds(CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId));
        Map<String, TradePlanDO> planMap = CollectionUtils.convertMap(planList, TradePlanDO::getId);
        // 组名与 Clash 订阅渲染同源, 客户端按 groupName 展开节点
        Map<String, String> groupNames = buildGroupNames(subs,
                CollectionUtils.convertMap(planList, TradePlanDO::getId, TradePlanDO::getName));
        List<PortalSubscriptionRespVO> result = new ArrayList<>(subs.size());
        // 响应客户端订阅视图
        for (TradeSubscriptionDO sub : subs) {
            TradePlanDO plan = planMap.get(sub.getPlanId());
            PortalSubscriptionRespVO vo = new PortalSubscriptionRespVO();
            vo.setSubscriptionId(sub.getId());
            vo.setStatus(sub.getStatus());
            vo.setStartedAt(sub.getStartedAt());
            vo.setExpiresAt(sub.getExpiresAt());
            vo.setGroupName(groupNames.get(sub.getId()));
            if (ObjectUtil.isNotNull(plan)) {
                vo.setPlanName(plan.getName());
                vo.setTrafficGb(plan.getTrafficGb());
            }
            // 订阅当前剩余可用额度
            vo.setRemainingBytes(tradeSubscriptionQuotaService.remainingBytes(sub.getId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public String renderSubscription(String subToken, String format) {
        boolean clash = "clash".equalsIgnoreCase(format);
        // token 无效 / 会员被禁用 → 返 null, 上层转 404 (不暴露"token 存在但禁用"的区别)
        MemberSubscriberDTO member = memberUserApi.getActiveBySubToken(subToken);
        if (member == null) {
            return null;
        }
        // 会员名下全部 ACTIVE 订阅聚合成一份订阅; 无订阅返空内容 (客户端导入得到空列表, 不报错)
        List<TradeSubscriptionDO> subs = subMapper.selectActiveByMember(member.getId());
        if (CollUtil.isEmpty(subs)) {
            return clash ? EMPTY_CLASH : Base64.encode("");
        }
        // 订阅 → 应运行凭证 (含连接身份/分配); 一个订阅可多凭证 (多 IP)
        Set<String> subIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getId);
        Map<String, TradeSubscriptionDO> subById = CollectionUtils.convertMap(subs, TradeSubscriptionDO::getId);
        List<TradeSubscriptionCertificateDO> certs = new ArrayList<>();
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscriptionIds(subIds)) {
            // 仅应运行且已分配线路机的凭证能拼连接; 刚下单未对账的(无 server)跳过
            if (TradeCertStatusEnum.ACTIVE.matches(cert.getCertStatus())
                    && ObjectUtil.isNotNull(cert.getServerId())
                    && subById.containsKey(cert.getSubscriptionId())) {
                certs.add(cert);
            }
        }
        if (CollUtil.isEmpty(certs)) {
            return clash ? EMPTY_CLASH : Base64.encode("");
        }
        // 只缺 node 侧的线路机接入参数 (host/端口/协议/传输/path/tls); 凭证密钥 trade 自己有
        Set<String> serverIds = CollectionUtils.convertSet(certs, TradeSubscriptionCertificateDO::getServerId);
        Map<String, XrayInboundDTO> inboundMap = xrayInboundApi.listInboundByServerIds(serverIds);
        // 套餐名只用于节点备注展示, 批量查一次避免逐订阅查库
        Set<String> planIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId);
        Map<String, String> planNameMap = CollectionUtils.convertMap(
                planMapper.selectBatchIds(planIds), TradePlanDO::getId, TradePlanDO::getName);

        if (clash) {
            return renderClashYaml(subs, certs, inboundMap, planNameMap);
        }
        StringBuilder lines = new StringBuilder();
        for (TradeSubscriptionCertificateDO cert : certs) {
            XrayInboundDTO inbound = inboundMap.get(cert.getServerId());
            if (ObjectUtil.isNull(inbound)) {
                continue; // 线路机未装 xray / 拼不出 host
            }
            // 目前只生成 vmess; 协议明确非 vmess 的跳过 (保留多协议扩展位); 协议空按 vmess 默认
            if (StrUtil.isNotBlank(inbound.getProtocol()) && !PROTOCOL_VMESS.equalsIgnoreCase(inbound.getProtocol())) {
                log.warn("[renderSubscription] 跳过非 vmess 线路机: server={} protocol={}",
                        cert.getServerId(), inbound.getProtocol());
                continue;
            }
            TradeSubscriptionDO sub = subById.get(cert.getSubscriptionId());
            lines.append(buildVmessLink(inbound, cert.getAuthSecret(), sub, planNameMap.get(sub.getPlanId()))).append('\n');
        }
        return Base64.encode(lines.toString().trim());
    }

    /**
     * 拼单个 vmess:// 链接 (host = 线路机域名 / 出网 IP; uuid = 凭证密钥).
     */
    private String buildVmessLink(XrayInboundDTO inbound, String uuid, TradeSubscriptionDO sub, String planName) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("v", "2");
        v.put("ps", buildRemark(sub, planName));
        v.put("add", inbound.getHost());
        v.put("port", String.valueOf(ObjectUtil.isNull(inbound.getPort()) ? DEFAULT_PORT : inbound.getPort()));
        v.put("id", uuid);
        v.put("aid", "0");
        v.put("scy", "auto");
        v.put("net", StrUtil.blankToDefault(inbound.getTransport(), "ws"));
        v.put("type", "none");
        v.put("host", inbound.getHost());
        v.put("path", StrUtil.nullToEmpty(inbound.getWsPath()));
        v.put("tls", inbound.isTls() ? "tls" : "");
        if (inbound.isTls()) {
            v.put("sni", inbound.getHost());
        }
        return "vmess://" + Base64.encode(toJson(v));
    }

    /**
     * 订阅 → 组名 (= 套餐名, 同名套餐自第 2 份起加序号); clash 渲染与订阅列表共用, 保证两边一致.
     */
    private Map<String, String> buildGroupNames(List<TradeSubscriptionDO> subs, Map<String, String> planNameMap) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (TradeSubscriptionDO sub : subs) {
            String name = StrUtil.blankToDefault(planNameMap.get(sub.getPlanId()), "套餐");
            int seq = nameCount.merge(name, 1, Integer::sum);
            result.put(sub.getId(), seq > 1 ? name + " #" + seq : name);
        }
        return result;
    }

    /**
     * 渲染 Clash YAML: 1 套餐(订阅) = 1 手选组, 组内节点 = 该订阅的凭证 (主从);
     * 隐藏总出口组承接 MATCH, 绕大陆规则下 rule 模式即智能分流.
     */
    private String renderClashYaml(List<TradeSubscriptionDO> subs,
                                   List<TradeSubscriptionCertificateDO> certs,
                                   Map<String, XrayInboundDTO> inboundMap,
                                   Map<String, String> planNameMap) {
        Map<String, String> groupNames = buildGroupNames(subs, planNameMap);
        Map<String, List<TradeSubscriptionCertificateDO>> certsBySub = new LinkedHashMap<>();
        for (TradeSubscriptionCertificateDO cert : certs) {
            certsBySub.computeIfAbsent(cert.getSubscriptionId(), k -> new ArrayList<>()).add(cert);
        }
        List<Map<String, Object>> proxies = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (TradeSubscriptionDO sub : subs) {
            List<TradeSubscriptionCertificateDO> subCerts = certsBySub.get(sub.getId());
            if (CollUtil.isEmpty(subCerts)) {
                continue;
            }
            String groupName = groupNames.get(sub.getId());
            List<String> nodeNames = new ArrayList<>();
            for (TradeSubscriptionCertificateDO cert : subCerts) {
                XrayInboundDTO inbound = inboundMap.get(cert.getServerId());
                if (ObjectUtil.isNull(inbound)) {
                    continue; // 线路机未装 xray / 拼不出 host
                }
                if (StrUtil.isNotBlank(inbound.getProtocol()) && !PROTOCOL_VMESS.equalsIgnoreCase(inbound.getProtocol())) {
                    log.warn("[renderSubscription] 跳过非 vmess 线路机: server={} protocol={}",
                            cert.getServerId(), inbound.getProtocol());
                    continue;
                }
                String nodeName = groupName + "-" + (nodeNames.size() + 1);
                nodeNames.add(nodeName);
                proxies.add(buildClashVmessProxy(nodeName, inbound, cert.getAuthSecret()));
            }
            if (nodeNames.isEmpty()) {
                continue;
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", groupName);
            group.put("type", "select");
            group.put("proxies", nodeNames);
            groups.add(group);
        }
        if (CollUtil.isEmpty(proxies)) {
            return EMPTY_CLASH;
        }
        Map<String, Object> rootGroup = new LinkedHashMap<>();
        rootGroup.put("name", CLASH_ROOT_GROUP);
        rootGroup.put("type", "select");
        rootGroup.put("hidden", true);
        rootGroup.put("proxies", groups.stream().map(g -> (String) g.get("name")).toList());
        groups.add(rootGroup);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("proxies", proxies);
        root.put("proxy-groups", groups);
        root.put("rules", List.of(
                "GEOSITE,CN,DIRECT",
                "GEOIP,LAN,DIRECT",
                "GEOIP,CN,DIRECT",
                "MATCH," + CLASH_ROOT_GROUP));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        return new Yaml(options).dump(root);
    }

    /**
     * 拼单个 Clash vmess 节点 (字段与 vmess:// 链接同源).
     */
    private Map<String, Object> buildClashVmessProxy(String name, XrayInboundDTO inbound, String uuid) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("type", "vmess");
        p.put("server", inbound.getHost());
        p.put("port", ObjectUtil.isNull(inbound.getPort()) ? DEFAULT_PORT : inbound.getPort());
        p.put("uuid", uuid);
        p.put("alterId", 0);
        p.put("cipher", "auto");
        p.put("udp", true);
        String network = StrUtil.blankToDefault(inbound.getTransport(), "ws");
        p.put("network", network);
        if (inbound.isTls()) {
            p.put("tls", true);
            p.put("servername", inbound.getHost());
        }
        if ("ws".equals(network)) {
            Map<String, Object> wsOpts = new LinkedHashMap<>();
            wsOpts.put("path", StrUtil.blankToDefault(inbound.getWsPath(), "/"));
            wsOpts.put("headers", Map.of("Host", inbound.getHost()));
            p.put("ws-opts", wsOpts);
        }
        return p;
    }

    /**
     * 节点备注: "套餐名 | 到期 MM-dd".
     */
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

}
