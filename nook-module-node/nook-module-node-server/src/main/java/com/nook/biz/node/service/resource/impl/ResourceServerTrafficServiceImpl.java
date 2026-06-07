package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerTrafficMapper;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
import com.nook.common.utils.unit.TrafficUnitUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private ResourceServerQuotaMapper resourceServerQuotaMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;

    /** 全平台流量重置时区; 显式配置, 不依赖 OS 默认. */
    @Value("${nook.traffic.reset-zone}")
    private String resetZone;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyNicTraffic(String serverId, long cumRxBytes, long cumTxBytes, Long bizUpBytes, Long bizDownBytes) {
        ResourceServerQuotaDO quota = resourceServerQuotaMapper.selectById(serverId);
        if (ObjectUtil.isNull(quota)) {
            return; // 额度配置行装机时已建; 缺失则跳过(理论不发生)
        }
        LocalDate today = LocalDate.now(ZoneId.of(resetZone));

        ResourceServerTrafficDO row = resourceServerTrafficMapper.selectCurrentByServerId(serverId);
        boolean isNew = ObjectUtil.isNull(row);
        if (isNew) {
            row = this.newCurrentRow(serverId, quota, today);
        } else {
            row = this.rolloverIfNeeded(quota, row, today);
            isNew = ObjectUtil.isNull(row.getId());
        }

        this.accumulate(row, cumRxBytes, cumTxBytes);
        this.markQuotaReached(quota, row);
        if (ObjectUtil.isNotNull(bizUpBytes)) {
            row.setCounterUpBytes(bizUpBytes); // 业务上行累计(绝对值); 增量计量在 trade 侧, 此处只覆盖, 跨周期不清零
        }
        if (ObjectUtil.isNotNull(bizDownBytes)) {
            row.setCounterDownBytes(bizDownBytes);
        }
        row.setLastSampledAt(LocalDateTime.now());
        if (isNew) {
            resourceServerTrafficMapper.insert(row);
        } else {
            resourceServerTrafficMapper.updateById(row);
        }
    }

    @Override
    public ResourceServerTrafficDO getCurrent(String serverId) {
        return resourceServerTrafficMapper.selectCurrentByServerId(serverId);
    }

    /** 建当周期首行(未入库, id 空); 周期起点按重置策略定, 基线由 accumulate 首见建立. */
    private ResourceServerTrafficDO newCurrentRow(String serverId, ResourceServerQuotaDO quota, LocalDate today) {
        ResourceServerTrafficDO row = new ResourceServerTrafficDO();
        row.setServerId(serverId);
        row.setStartTime(this.periodStart(quota, today));
        row.setRxBytes(0L);
        row.setTxBytes(0L);
        row.setUsedBytes(0L);
        row.setThrottleState(ResourceServerThrottleStateEnum.NORMAL.getState());
        return row;
    }

    /** 周期翻篇: 跨过我方重置日 → 封存当前行(填 end_time), 返回带过游标的新当周期行(未入库); 否则原样返回. FIXED 永不翻篇. */
    private ResourceServerTrafficDO rolloverIfNeeded(ResourceServerQuotaDO quota, ResourceServerTrafficDO current,
                                                     LocalDate today) {
        if (ResourceServerQuotaResetPolicyEnum.FIXED.matches(quota.getResetPolicy())) {
            return current;
        }
        LocalDate periodStart = this.periodStart(quota, today);
        if (ObjectUtil.isNull(current.getStartTime()) || !current.getStartTime().isBefore(periodStart)) {
            return current;
        }
        current.setEndTime(periodStart);
        resourceServerTrafficMapper.updateById(current);
        ResourceServerTrafficDO next = new ResourceServerTrafficDO();
        next.setServerId(current.getServerId());
        next.setStartTime(periodStart);
        next.setRxBytes(0L);
        next.setTxBytes(0L);
        next.setUsedBytes(0L);
        next.setLastCounterRxBytes(current.getLastCounterRxBytes()); // 游标带过去, 新周期接着做差
        next.setLastCounterTxBytes(current.getLastCounterTxBytes());
        next.setCounterUpBytes(current.getCounterUpBytes());         // 业务累计跨周期不清零
        next.setCounterDownBytes(current.getCounterDownBytes());
        next.setThrottleState(ResourceServerThrottleStateEnum.NORMAL.getState());
        return next;
    }

    /** 增量累加 (抗计数器归零 / 整机重置 / 首见). */
    private void accumulate(ResourceServerTrafficDO row, long cumRx, long cumTx) {
        if (ObjectUtil.isNull(row.getLastCounterRxBytes())) {
            // 首见仅建基准, 不把历史全量计入
            row.setLastCounterRxBytes(cumRx);
            row.setLastCounterTxBytes(cumTx);
            return;
        }
        long dRx = cumRx >= row.getLastCounterRxBytes() ? cumRx - row.getLastCounterRxBytes() : cumRx;
        long dTx = cumTx >= row.getLastCounterTxBytes() ? cumTx - row.getLastCounterTxBytes() : cumTx;
        row.setRxBytes(nz(row.getRxBytes()) + dRx);
        row.setTxBytes(nz(row.getTxBytes()) + dTx);
        row.setUsedBytes(row.getRxBytes() + row.getTxBytes());
        row.setLastCounterRxBytes(cumRx);
        row.setLastCounterTxBytes(cumTx);
    }

    /** 配额到顶置已限流 (停分新用户 + 触发同地区切换, 上层消费); 不限额则不动. */
    private void markQuotaReached(ResourceServerQuotaDO quota, ResourceServerTrafficDO row) {
        Integer totalGb = quota.getTotalGb();
        if (ObjectUtil.isNotNull(totalGb) && totalGb > 0 && nz(row.getUsedBytes()) >= TrafficUnitUtils.gbToBytes(totalGb)) {
            row.setThrottleState(ResourceServerThrottleStateEnum.THROTTLED.getState());
        }
    }

    /** 周期起点: 按月 = ≤today 最近一个重置日(clamp 1..28); 固定不重置 = today. */
    private LocalDate periodStart(ResourceServerQuotaDO quota, LocalDate today) {
        if (ResourceServerQuotaResetPolicyEnum.FIXED.matches(quota.getResetPolicy())) {
            return today;
        }
        int resetDay = this.resolveResetDay(quota);
        return today.getDayOfMonth() >= resetDay
                ? today.withDayOfMonth(resetDay)
                : today.minusMonths(1).withDayOfMonth(resetDay);
    }

    /** 我方重置日 = quota.reset_day(clamp 1..28); 取不到用缺省. */
    private int resolveResetDay(ResourceServerQuotaDO quota) {
        Integer day = quota.getResetDay();
        return (ObjectUtil.isNull(day) || day < 1 || day > 28) ? DEFAULT_RESET_DAY : day;
    }

    private static long nz(Long v) {
        return ObjectUtil.isNull(v) ? 0L : v;
    }
}
