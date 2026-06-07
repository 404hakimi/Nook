package com.nook.biz.trade.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.ResourceServerQuotaApi;
import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionTrafficDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionTrafficMapper;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.biz.trade.service.TradeSubscriptionQuotaService;
import com.nook.biz.trade.service.TradeTrafficMeteringService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.unit.TrafficUnitUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅流量计量 Service 实现类
 *
 * @author nook
 */
@Service
public class TradeTrafficMeteringServiceImpl implements TradeTrafficMeteringService {

    /** 流量重置周期(天); 30 天一个周期, 从开通时刻起算(保留时分秒). */
    private static final int CYCLE_RESET_DAYS = 30;

    @Resource
    private TradePlanMapper tradePlanMapper;
    @Resource
    private TradeSubscriptionTrafficMapper tradeSubscriptionTrafficMapper;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private ResourceServerQuotaApi resourceServerQuotaApi;
    @Resource
    private TradeSubscriptionQuotaService tradeSubscriptionQuotaService;

    @Override
    public MeteringContext preload(List<TradeSubscriptionDO> subs) {
        // 套餐月流量配额
        Set<String> planIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId);
        Map<String, Integer> planTrafficGb = CollectionUtils.convertMap(
                tradePlanMapper.selectBatchIds(planIds), TradePlanDO::getId,
                p -> ObjectUtil.isNull(p.getTrafficGb()) ? 0 : p.getTrafficGb());
        // 订阅 → 已分配落地机的接入点(凭证), 按订阅分组
        Set<String> subIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getId);
        Map<String, List<TradeSubscriptionCertificateDO>> certsBySub = new HashMap<>();
        Set<String> landingIds = new HashSet<>();
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscriptionIds(subIds)) {
            if (ObjectUtil.isNull(cert.getIpId())) {
                continue; // 未分配落地机, 不计量
            }
            certsBySub.computeIfAbsent(cert.getSubscriptionId(), k -> new ArrayList<>()).add(cert);
            landingIds.add(cert.getIpId());
        }
        Map<String, ResourceServerQuotaRespDTO> capByLanding = landingIds.isEmpty()
                ? Map.of() : resourceServerQuotaApi.listByServerIds(landingIds);
        // 各接入点的当周期计量行
        Map<String, TradeSubscriptionTrafficDO> trafficByCert = CollectionUtils.convertMap(
                tradeSubscriptionTrafficMapper.selectCurrentBySubscriptionIds(subIds),
                TradeSubscriptionTrafficDO::getCertId);
        return new MeteringContext(planTrafficGb, certsBySub, capByLanding, trafficByCert);
    }

    @Override
    public boolean accumulate(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        // 跨周期先翻篇(接入点行封存开新行 + 发新基础额度), 再累加本轮增量
        this.rolloverIfDue(s, now, ctx);
        long totalDelta = 0L;
        for (TradeSubscriptionCertificateDO cert : ctx.certsBySub().getOrDefault(s.getId(), List.of())) {
            totalDelta += this.meterCert(s, cert, now, ctx);
        }
        if (totalDelta > 0) {
            tradeSubscriptionQuotaService.addUsage(s.getId(), totalDelta);
        }
        return tradeSubscriptionQuotaService.remainingBytes(s.getId()) <= 0;
    }

    @Override
    public boolean tryCycleReset(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        return this.rolloverIfDue(s, now, ctx);
    }

    /** 单接入点本轮上下行增量累加(抗归零 / 换落地机重打游标 / 首见只建基线); 返回本轮增量(上+下). */
    private long meterCert(TradeSubscriptionDO s, TradeSubscriptionCertificateDO cert,
                           LocalDateTime now, MeteringContext ctx) {
        String landingId = cert.getIpId();
        ResourceServerQuotaRespDTO cap = ctx.capByLanding().get(landingId);
        if (ObjectUtil.isNull(cap)) {
            return 0L;
        }
        Long up = cap.getCounterUpBytes();
        Long down = cap.getCounterDownBytes();
        if (ObjectUtil.isNull(up) && ObjectUtil.isNull(down)) {
            return 0L; // 落地机还没上报业务流量
        }
        TradeSubscriptionTrafficDO row = ctx.trafficByCert().get(cert.getId());
        if (ObjectUtil.isNull(row)) {
            // 首见: 建当周期行, 基线=当前 (不补历史)
            row = new TradeSubscriptionTrafficDO();
            row.setCertId(cert.getId());
            row.setSubscriptionId(s.getId());
            row.setLandingServerId(landingId);
            row.setStartTime(this.cycleStart(s, now));
            row.setUpBytes(0L);
            row.setDownBytes(0L);
            row.setUsedBytes(0L);
            row.setLastCounterUpBytes(up);
            row.setLastCounterDownBytes(down);
            row.setLastSampledAt(now);
            tradeSubscriptionTrafficMapper.insert(row);
            ctx.trafficByCert().put(cert.getId(), row);
            return 0L;
        }
        if (!landingId.equals(row.getLandingServerId())) {
            // 换落地机: 重打游标, 不补增量
            row.setLandingServerId(landingId);
            row.setLastCounterUpBytes(up);
            row.setLastCounterDownBytes(down);
            row.setLastSampledAt(now);
            tradeSubscriptionTrafficMapper.updateById(row);
            return 0L;
        }
        long dUp = this.delta(up, row.getLastCounterUpBytes());
        long dDown = this.delta(down, row.getLastCounterDownBytes());
        row.setUpBytes(nz(row.getUpBytes()) + dUp);
        row.setDownBytes(nz(row.getDownBytes()) + dDown);
        row.setUsedBytes(nz(row.getUpBytes()) + nz(row.getDownBytes()));
        if (ObjectUtil.isNotNull(up)) {
            row.setLastCounterUpBytes(up);
        }
        if (ObjectUtil.isNotNull(down)) {
            row.setLastCounterDownBytes(down);
        }
        row.setLastSampledAt(now);
        tradeSubscriptionTrafficMapper.updateById(row);
        return dUp + dDown;
    }

    /** 跨周期: 各接入点当周期行封存(填 end_time) + 开新行(游标带过去, 用量归零) + 发下一周期基础额度; 返回是否翻篇. */
    private boolean rolloverIfDue(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        List<TradeSubscriptionCertificateDO> certs = ctx.certsBySub().getOrDefault(s.getId(), List.of());
        if (certs.isEmpty()) {
            return false;
        }
        LocalDateTime cycleStart = this.cycleStart(s, now);
        boolean rolled = false;
        for (TradeSubscriptionCertificateDO cert : certs) {
            TradeSubscriptionTrafficDO row = ctx.trafficByCert().get(cert.getId());
            if (ObjectUtil.isNull(row) || ObjectUtil.isNull(row.getStartTime())
                    || !row.getStartTime().isBefore(cycleStart)) {
                continue;
            }
            row.setEndTime(cycleStart);
            tradeSubscriptionTrafficMapper.updateById(row);
            TradeSubscriptionTrafficDO next = new TradeSubscriptionTrafficDO();
            next.setCertId(cert.getId());
            next.setSubscriptionId(s.getId());
            next.setLandingServerId(row.getLandingServerId());
            next.setStartTime(cycleStart);
            next.setUpBytes(0L);
            next.setDownBytes(0L);
            next.setUsedBytes(0L);
            next.setLastCounterUpBytes(row.getLastCounterUpBytes()); // 游标带过去, 新周期接着做差
            next.setLastCounterDownBytes(row.getLastCounterDownBytes());
            next.setLastSampledAt(now);
            tradeSubscriptionTrafficMapper.insert(next);
            ctx.trafficByCert().put(cert.getId(), next);
            rolled = true;
        }
        if (rolled) {
            int gb = ctx.planTrafficGb().getOrDefault(s.getPlanId(), 0);
            tradeSubscriptionQuotaService.createBaseQuota(
                    s.getId(), TrafficUnitUtils.gbToBytes(gb), cycleStart, this.cycleEnd(s, cycleStart));
        }
        return rolled;
    }

    /** 当前周期起点 = 开通时刻 + 整数个周期(stateless 推算); 无开通时刻退化为 now. */
    private LocalDateTime cycleStart(TradeSubscriptionDO s, LocalDateTime now) {
        LocalDateTime start = s.getStartedAt();
        if (ObjectUtil.isNull(start)) {
            return now;
        }
        long days = ChronoUnit.DAYS.between(start, now);
        if (days < CYCLE_RESET_DAYS) {
            return start;
        }
        return start.plusDays(days / CYCLE_RESET_DAYS * CYCLE_RESET_DAYS);
    }

    /** 周期到期 = min(下个周期起点, 订阅到期); 额度不超过订阅有效期. */
    private LocalDateTime cycleEnd(TradeSubscriptionDO s, LocalDateTime cycleStart) {
        LocalDateTime next = cycleStart.plusDays(CYCLE_RESET_DAYS);
        LocalDateTime expiry = s.getExpiresAt();
        return (ObjectUtil.isNotNull(expiry) && expiry.isBefore(next)) ? expiry : next;
    }

    /** 累计值做差(抗归零): 当前 ≥ 上次取差, 否则按归零从当前重算; 当前空记 0. */
    private long delta(Long current, Long last) {
        if (ObjectUtil.isNull(current)) {
            return 0L;
        }
        long base = nz(last);
        return current >= base ? current - base : current;
    }

    private static long nz(Long v) {
        return ObjectUtil.isNull(v) ? 0L : v;
    }
}
