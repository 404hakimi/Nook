package com.nook.biz.trade.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.xray.XrayClientApi;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.MemberPlanTrafficMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
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

    @Override
    public MeteringContext preload(List<TradeSubscriptionDO> subs) {
        // 获取套餐id列表
        Set<String> planIds = CollectionUtils.convertSet(subs, TradeSubscriptionDO::getPlanId);
        // 获取对应套餐的月流量配额
        Map<String, Integer> planTrafficGb = CollectionUtils.convertMap(
                tradePlanMapper.selectBatchIds(planIds), TradePlanDO::getId,
                p -> p.getTrafficGb() == null ? 0 : p.getTrafficGb());
        // 获取这些订阅所关联的 xray客户端id
        Set<String> clientIds = CollectionUtils.convertSet(subs,
                TradeSubscriptionDO::getXrayClientId, s -> StrUtil.isNotBlank(s.getXrayClientId()));
        // 根据xray客户端id获取落地机
        Map<String, String> landingByClient = xrayClientApi.getLandingIdByClientIds(clientIds);
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
        String clientId = s.getXrayClientId();
        String landingId = ctx.landingByClient().get(clientId);
        if (landingId == null) {
            return false; // 无落地机绑定信息, 跳过本轮
        }
        ResourceServerCapacityRespDTO cap = ctx.capMap().get(landingId);
        if (cap == null) {
            return false;
        }
        // 优先用 socks5 业务流量(已排除 agent/系统); 老 agent 未上报 biz 时回退整机 tx
        Long cumSource = cap.getBizUsedBytes() != null ? cap.getBizUsedBytes() : cap.getTxBytes();
        if (cumSource == null) {
            return false; // 落地机还没上报, 本轮无数据
        }
        long cur = cumSource;

        MemberPlanTrafficDO row = ctx.trafficBySub().get(s.getId());
        if (row == null) {
            row = new MemberPlanTrafficDO();
            row.setSubscriptionId(s.getId());
            row.setMemberUserId(s.getMemberUserId());
            row.setLandingServerId(landingId);
            row.setUsedBytes(0L);
            row.setLastCounterTx(cur); // 首见: 基线=当前, used=0 (不继承落地机历史)
            row.setCycleResetAt(firstCycleReset(s)); // 流量重置锚点=开通时刻+30天; 不足一周期返 null
            row.setLastSampledAt(now);
            memberPlanTrafficMapper.insert(row);
        } else {
            long used = row.getUsedBytes() == null ? 0L : row.getUsedBytes();
            long last = row.getLastCounterTx() == null ? cur : row.getLastCounterTx();
            if (!landingId.equals(row.getLandingServerId())) {
                row.setLandingServerId(landingId); // 换落地机: 重基线, used 保留
                row.setLastCounterTx(cur);
            } else if (cur < last) {
                row.setLastCounterTx(cur);         // 计数回退: 只挪游标, used 不动
            } else {
                row.setUsedBytes(used + (cur - last)); // 正常累加
                row.setLastCounterTx(cur);
            }
            // 流量周期重置: 到锚点(+30天)清零 + 重打基线 + 推下一周期; 超到期不再设, 由 expiresAt 走过期
            if (row.getCycleResetAt() != null && !now.isBefore(row.getCycleResetAt())) {
                row.setUsedBytes(0L);
                row.setLastCounterTx(cur);
                LocalDateTime next = row.getCycleResetAt().plusDays(CYCLE_RESET_DAYS);
                row.setCycleResetAt(s.getExpiresAt() != null && next.isBefore(s.getExpiresAt()) ? next : null);
            }
            row.setLastSampledAt(now);
            memberPlanTrafficMapper.updateById(row);
        }

        int gb = ctx.planTrafficGb().getOrDefault(s.getPlanId(), 0);
        long usedNow = row.getUsedBytes() == null ? 0L : row.getUsedBytes();
        return gb > 0 && usedNow >= TrafficUnitUtils.gbToBytes(gb);
    }

    @Override
    public boolean tryCycleReset(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        MemberPlanTrafficDO row = ctx.trafficBySub().get(s.getId());
        if (row == null || row.getCycleResetAt() == null || now.isBefore(row.getCycleResetAt())) {
            return false; // 没到重置点, 继续停服
        }
        row.setUsedBytes(0L);
        Long cur = currentBiz(s.getXrayClientId(), ctx);
        if (cur != null) {
            row.setLastCounterTx(cur); // 重打基线: 恢复后从当前累计起算
        }
        LocalDateTime next = row.getCycleResetAt().plusDays(CYCLE_RESET_DAYS);
        row.setCycleResetAt(s.getExpiresAt() != null && next.isBefore(s.getExpiresAt()) ? next : null);
        row.setLastSampledAt(now);
        memberPlanTrafficMapper.updateById(row);
        return true;
    }

    /**
     * 流量重置锚点: 开通时刻 + 30 天(保留时分秒); 不足一个重置周期(锚点 ≥ 到期)则返 null.
     */
    private LocalDateTime firstCycleReset(TradeSubscriptionDO s) {
        if (s.getStartedAt() == null) {
            return null;
        }
        LocalDateTime first = s.getStartedAt().plusDays(CYCLE_RESET_DAYS);
        if (s.getExpiresAt() != null && !first.isBefore(s.getExpiresAt())) {
            return null;
        }
        return first;
    }

    /**
     * 取某 client 当前落地机的业务流量累计 (biz 优先, 回退整机 tx); 无数据返 null.
     */
    private Long currentBiz(String clientId, MeteringContext ctx) {
        String landingId = ctx.landingByClient().get(clientId);
        if (landingId == null) {
            return null;
        }
        ResourceServerCapacityRespDTO cap = ctx.capMap().get(landingId);
        if (cap == null) {
            return null;
        }
        return cap.getBizUsedBytes() != null ? cap.getBizUsedBytes() : cap.getTxBytes();
    }
}
