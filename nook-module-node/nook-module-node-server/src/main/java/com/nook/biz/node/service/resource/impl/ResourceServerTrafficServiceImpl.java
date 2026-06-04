package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerTrafficMapper;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
import com.nook.common.utils.unit.TrafficUnitUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 服务器流量计量 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerTrafficServiceImpl implements ResourceServerTrafficService {

    /** 重置日缺省值; 取不到账单日时用 1 号. */
    private static final int DEFAULT_RESET_DAY = 1;

    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;

    /** 全平台流量重置时区; 显式配置, 不依赖 OS 默认. */
    @Value("${nook.traffic.reset-zone}")
    private String resetZone;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyNicTraffic(String serverId, long cumRxBytes, long cumTxBytes, Long bizUsedBytes) {
        ResourceServerCapacityDO cap = resourceServerCapacityMapper.selectById(serverId);
        if (ObjectUtil.isNull(cap)) {
            return; // 容量行装机时已建; 缺失则跳过(理论不发生)
        }
        LocalDate today = LocalDate.now(ZoneId.of(resetZone));

        this.rolloverIfNeeded(cap, today);
        this.accumulate(cap, cumRxBytes, cumTxBytes);
        this.markQuotaReached(cap);
        if (ObjectUtil.isNotNull(bizUsedBytes)) {
            cap.setBizUsedBytes(bizUsedBytes); // socks5 业务流量累计(绝对值); 增量计量在 TradeLifecycleJob, 此处只覆盖, 不随周期清零
        }

        cap.setUpdatedAt(LocalDateTime.now());
        resourceServerCapacityMapper.updateById(cap);
    }

    /** 周期翻篇: 跨过我方重置日则归档旧周期并清零 (FIXED 永不翻篇). */
    private void rolloverIfNeeded(ResourceServerCapacityDO cap, LocalDate today) {
        if (ResourceServerQuotaResetPolicyEnum.FIXED.matches(cap.getQuotaResetPolicy())) {
            if (ObjectUtil.isNull(cap.getPeriodStart())) {
                cap.setPeriodStart(today); // 仅记起点, 之后不再推进
            }
            return;
        }
        LocalDate periodStart = this.currentPeriodStart(cap, today);
        if (ObjectUtil.isNull(cap.getPeriodStart())) {
            cap.setPeriodStart(periodStart); // 首次建周期, 不归档
        } else if (cap.getPeriodStart().isBefore(periodStart)) {
            this.archive(cap, periodStart);
            cap.setRxBytes(0L);
            cap.setTxBytes(0L);
            cap.setUsedTrafficBytes(0L);
            cap.setPeriodStart(periodStart);
            cap.setThrottleState(ResourceServerThrottleStateEnum.NORMAL.getState());
        }
    }

    /** 增量累加 (抗计数器归零 / 整机重置 / 首次上报). */
    private void accumulate(ResourceServerCapacityDO cap, long cumRx, long cumTx) {
        if (ObjectUtil.isNull(cap.getLastCumRxBytes())) {
            // 首次仅建基准, 不把历史全量计入
            cap.setLastCumRxBytes(cumRx);
            cap.setLastCumTxBytes(cumTx);
            return;
        }
        long dRx = cumRx >= cap.getLastCumRxBytes() ? cumRx - cap.getLastCumRxBytes() : cumRx;
        long dTx = cumTx >= cap.getLastCumTxBytes() ? cumTx - cap.getLastCumTxBytes() : cumTx;
        cap.setRxBytes(nz(cap.getRxBytes()) + dRx);
        cap.setTxBytes(nz(cap.getTxBytes()) + dTx);
        cap.setUsedTrafficBytes(cap.getRxBytes() + cap.getTxBytes());
        cap.setLastCumRxBytes(cumRx);
        cap.setLastCumTxBytes(cumTx);
    }

    /** 配额到顶置为已触发限流 (停止分新用户 + 触发同地区切换, 由上层消费); 不限额则不动. */
    private void markQuotaReached(ResourceServerCapacityDO cap) {
        Integer quotaGb = cap.getMonthlyTrafficGb();
        if (ObjectUtil.isNotNull(quotaGb) && quotaGb > 0 && nz(cap.getUsedTrafficBytes()) >= TrafficUnitUtils.gbToBytes(quotaGb)) {
            cap.setThrottleState(ResourceServerThrottleStateEnum.THROTTLED.getState());
        }
    }

    /** 当前"按月"周期起点 = ≤today 的最近一个重置日 (clamp 1..28). */
    private LocalDate currentPeriodStart(ResourceServerCapacityDO cap, LocalDate today) {
        int resetDay = this.resolveResetDay(cap);
        return today.getDayOfMonth() >= resetDay
                ? today.withDayOfMonth(resetDay)
                : today.minusMonths(1).withDayOfMonth(resetDay);
    }

    /** 我方重置日 = capacity.reset_day(clamp 1..28); 取不到用缺省. */
    private int resolveResetDay(ResourceServerCapacityDO cap) {
        Integer day = cap.getResetDay();
        return (ObjectUtil.isNull(day) || day < 1 || day > 28) ? DEFAULT_RESET_DAY : day;
    }

    /** 把结束的周期写入历史表; (server_id, period_start) 唯一, 重复归档忽略. */
    private void archive(ResourceServerCapacityDO cap, LocalDate periodEnd) {
        ResourceServerTrafficDO row = new ResourceServerTrafficDO();
        row.setServerId(cap.getServerId());
        row.setPeriodStart(cap.getPeriodStart());
        row.setPeriodEnd(periodEnd);
        row.setRxBytes(nz(cap.getRxBytes()));
        row.setTxBytes(nz(cap.getTxBytes()));
        row.setUsedBytes(nz(cap.getUsedTrafficBytes()));
        try {
            resourceServerTrafficMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.warn("[archive] 周期已归档, 跳过: serverId={} periodStart={}", cap.getServerId(), cap.getPeriodStart());
        }
    }

    private static long nz(Long v) {
        return ObjectUtil.isNull(v) ? 0L : v;
    }
}
