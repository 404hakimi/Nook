package com.nook.biz.trade.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.xray.XrayClientApi;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.MemberPlanTrafficMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.service.TradeTrafficGrantService;
import com.nook.biz.trade.service.TradeTrafficMeteringService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.unit.TrafficUnitUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    /**
     * 流量重置周期(天); 默认 30 天一个周期, 从开通时刻起算(保留时分秒).
     */
    private static final int CYCLE_RESET_DAYS = 30;

    @Resource
    private TradePlanMapper tradePlanMapper;
    @Resource
    private MemberPlanTrafficMapper memberPlanTrafficMapper;
    @Resource
    private XrayClientApi xrayClientApi;
    @Resource
    private ResourceServerCapacityApi resourceServerCapacityApi;
    @Resource
    private TradeTrafficGrantService tradeTrafficGrantService;

    @Override
    public MeteringContext preload(List<TradeSubscriptionDO> subs) {
        // 套餐月流量配额
        Set<String> planIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId);
        Map<String, Integer> planTrafficGb = CollectionUtils.convertMap(
                tradePlanMapper.selectBatchIds(planIds), TradePlanDO::getId,
                p -> ObjectUtil.isNull(p.getTrafficGb()) ? 0 : p.getTrafficGb());
        // 订阅关联的凭证 → 落地机
        Set<String> certIds = CollectionUtils.convertSet(subs,
                TradeSubscriptionDO::getXrayClientId, s -> StrUtil.isNotBlank(s.getXrayClientId()));
        Map<String, String> landingByClient = xrayClientApi.getLandingIdByClientIds(certIds);
        Set<String> landingIds = new HashSet<>(landingByClient.values());
        Map<String, ResourceServerCapacityRespDTO> capMap = CollUtil.isEmpty(landingIds)
                ? Map.of() : resourceServerCapacityApi.listByServerIds(landingIds);
        Map<String, MemberPlanTrafficDO> trafficBySub = CollectionUtils.convertMap(
                memberPlanTrafficMapper.selectBatchIds(CollectionUtils.convertSet(subs, TradeSubscriptionDO::getId)),
                MemberPlanTrafficDO::getSubscriptionId);
        return new MeteringContext(planTrafficGb, landingByClient, capMap, trafficBySub);
    }

    @Override
    public boolean accumulate(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        String certId = s.getXrayClientId();
        String landingId = ctx.landingByClient().get(certId);
        if (ObjectUtil.isNull(landingId)) {
            return false; // 无落地机绑定信息, 跳过本轮
        }
        ResourceServerCapacityRespDTO cap = ctx.capMap().get(landingId);
        if (ObjectUtil.isNull(cap)) {
            return false;
        }
        // 优先用 socks5 业务流量(已排除 agent/系统); 老 agent 未上报时回退整机出站累计
        Long cumSource = ObjectUtil.isNotNull(cap.getBizUsedBytes()) ? cap.getBizUsedBytes() : cap.getTxBytes();
        if (ObjectUtil.isNull(cumSource)) {
            return false; // 落地机还没上报, 本轮无数据
        }
        long cur = cumSource;

        MemberPlanTrafficDO row = ctx.trafficBySub().get(s.getId());
        long delta = 0L; // 本轮新增用量, 累加到额度授予
        if (ObjectUtil.isNull(row)) {
            // 首见: 建计量游标, 基线=当前 (不继承落地机历史)
            row = new MemberPlanTrafficDO();
            row.setSubscriptionId(s.getId());
            row.setMemberUserId(s.getMemberUserId());
            row.setLandingServerId(landingId);
            row.setUsedBytes(0L);
            row.setLastCounterTx(cur);
            row.setCycleResetAt(this.firstCycleReset(s)); // 重置锚点=开通时刻+周期天数; 不足一周期返 null
            row.setLastSampledAt(now);
            memberPlanTrafficMapper.insert(row);
        } else {
            long used = ObjectUtil.isNull(row.getUsedBytes()) ? 0L : row.getUsedBytes();
            long last = ObjectUtil.isNull(row.getLastCounterTx()) ? cur : row.getLastCounterTx();
            if (!landingId.equals(row.getLandingServerId())) {
                row.setLandingServerId(landingId); // 换落地机: 重打基线, 不补增量
                row.setLastCounterTx(cur);
            } else if (cur < last) {
                row.setLastCounterTx(cur);         // 计数回退: 只挪游标
            } else {
                delta = cur - last;                // 正常累加
                row.setUsedBytes(used + delta);
                row.setLastCounterTx(cur);
            }
            // 周期重置(多周期订阅): 到锚点 → 游标清零 + 发下一周期基础额度 + 推下一锚点
            if (ObjectUtil.isNotNull(row.getCycleResetAt()) && !now.isBefore(row.getCycleResetAt())) {
                this.rollover(s, row, cur, ctx);
            }
            row.setLastSampledAt(now);
            memberPlanTrafficMapper.updateById(row);
        }

        // 本轮增量累加到额度授予; 是否耗尽按"生效且未到期额度之和"判定
        if (delta > 0) {
            tradeTrafficGrantService.addUsage(s.getId(), delta);
        }
        return tradeTrafficGrantService.remainingBytes(s.getId()) <= 0;
    }

    @Override
    public boolean tryCycleReset(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        MemberPlanTrafficDO row = ctx.trafficBySub().get(s.getId());
        if (ObjectUtil.isNull(row) || ObjectUtil.isNull(row.getCycleResetAt()) || now.isBefore(row.getCycleResetAt())) {
            return false; // 没到重置点, 继续停服
        }
        // 取不到当前累计值则沿用旧基线
        Long cur = this.currentBiz(s.getXrayClientId(), ctx);
        long cursor = ObjectUtil.isNotNull(cur) ? cur
                : (ObjectUtil.isNull(row.getLastCounterTx()) ? 0L : row.getLastCounterTx());
        this.rollover(s, row, cursor, ctx);
        row.setLastSampledAt(now);
        memberPlanTrafficMapper.updateById(row);
        return true;
    }

    /** 周期翻篇: 计量游标清零并重打基线 + 发下一周期基础额度 (旧额度到期自动失效, 不重复计). */
    private void rollover(TradeSubscriptionDO s, MemberPlanTrafficDO row, long cursor, MeteringContext ctx) {
        row.setUsedBytes(0L);
        row.setLastCounterTx(cursor);
        LocalDateTime cycleStart = row.getCycleResetAt();
        LocalDateTime next = cycleStart.plusDays(CYCLE_RESET_DAYS);
        boolean hasNext = ObjectUtil.isNotNull(s.getExpiresAt()) && next.isBefore(s.getExpiresAt());
        row.setCycleResetAt(hasNext ? next : null);
        int gb = ctx.planTrafficGb().getOrDefault(s.getPlanId(), 0);
        LocalDateTime grantExpiry = hasNext ? next : s.getExpiresAt();
        tradeTrafficGrantService.createBaseGrant(s.getId(), TrafficUnitUtils.gbToBytes(gb), cycleStart, grantExpiry);
    }

    /**
     * 流量重置锚点: 开通时刻 + 30 天(保留时分秒); 不足一个重置周期(锚点 ≥ 到期)则返 null.
     */
    private LocalDateTime firstCycleReset(TradeSubscriptionDO s) {
        if (ObjectUtil.isNull(s.getStartedAt())) {
            return null;
        }
        LocalDateTime first = s.getStartedAt().plusDays(CYCLE_RESET_DAYS);
        if (ObjectUtil.isNotNull(s.getExpiresAt()) && !first.isBefore(s.getExpiresAt())) {
            return null;
        }
        return first;
    }

    /**
     * 取某接入点当前落地机的业务流量累计 (业务流量优先, 回退整机出站累计); 无数据返 null.
     */
    private Long currentBiz(String certId, MeteringContext ctx) {
        String landingId = ctx.landingByClient().get(certId);
        if (ObjectUtil.isNull(landingId)) {
            return null;
        }
        ResourceServerCapacityRespDTO cap = ctx.capMap().get(landingId);
        if (ObjectUtil.isNull(cap)) {
            return null;
        }
        return ObjectUtil.isNotNull(cap.getBizUsedBytes()) ? cap.getBizUsedBytes() : cap.getTxBytes();
    }
}
