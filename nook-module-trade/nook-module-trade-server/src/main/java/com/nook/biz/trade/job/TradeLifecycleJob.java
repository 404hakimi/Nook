package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.service.TradeTrafficMeteringService;
import com.nook.biz.trade.service.TradeTrafficMeteringService.MeteringContext;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅生命周期 Job: 到期释放 + 流量耗尽停服 + 周期重置恢复.
 *
 * <p>每轮以订阅为单位、各一个事务; 流量计量委托 {@link TradeTrafficMeteringService}, 本类只管调度与生命周期动作(吊销/停服/复活).
 *
 * @author nook
 */
@Slf4j
@Component
public class TradeLifecycleJob {

    @Resource
    private TradeSubscriptionMapper tradeSubscriptionMapper;
    @Resource
    private XrayClientProvisionApi xrayClientProvisionApi;
    @Resource
    private TradeTrafficMeteringService tradeTrafficMeteringService;
    @Resource
    private TransactionTemplate transactionTemplate;

    /** 单订阅处理结果, 用于汇总计数. */
    private enum Outcome { EXPIRED, SUSPENDED, RESUMED, NONE }

    @Scheduled(cron = "#{@tradeJobProperties.lifecycleCron}")
    public void check() {
        List<TradeSubscriptionDO> subs = tradeSubscriptionMapper.selectActiveOrSuspended(); // 含停服(到重置点要恢复)
        if (CollUtil.isEmpty(subs)) {
            return;
        }
        MeteringContext ctx = tradeTrafficMeteringService.preload(subs);
        LocalDateTime now = LocalDateTime.now();

        int expired = 0, suspended = 0, resumed = 0;
        for (TradeSubscriptionDO s : subs) {
            try {
                // 每订阅一个事务: 计量写 member_plan_traffic 与生命周期改订阅状态 原子提交
                Outcome o = transactionTemplate.execute(st -> processOne(s, now, ctx));
                if (o == Outcome.EXPIRED) {
                    expired++;
                } else if (o == Outcome.SUSPENDED) {
                    suspended++;
                } else if (o == Outcome.RESUMED) {
                    resumed++;
                }
            } catch (Exception e) {
                log.error("[lifecycle] sub={} client={} 处理失败: {}",
                        s.getId(), s.getXrayClientId(), e.getMessage(), e);
            }
        }
        if (expired + suspended + resumed > 0) {
            log.info("[lifecycle] 扫描完成: 总={} 到期释放={} 流量耗尽停服={} 重置恢复={}",
                    subs.size(), expired, suspended, resumed);
        }
    }

    /** 单订阅处理 (在调用方事务内): 到期释放 / 停服恢复 / 在线计量; 计量数学委托 tradeTrafficMeteringService. */
    private Outcome processOne(TradeSubscriptionDO s, LocalDateTime now, MeteringContext ctx) {
        // 到期 → 释放 (revoke + 标 EXPIRED 原子)
        if (s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now)) {
            doExpire(s);
            return Outcome.EXPIRED;
        }
        // 停服订阅: 到重置点则计量清零重打基线后复活
        if (TradeSubscriptionStatusEnum.SUSPENDED.matches(s.getStatus())) {
            if (tradeTrafficMeteringService.tryCycleReset(s, now, ctx)) {
                doResume(s);
                return Outcome.RESUMED;
            }
            return Outcome.NONE;
        }
        // ACTIVE: 累加业务流量; 达套餐上限 → 停服保留 IP
        if (tradeTrafficMeteringService.accumulate(s, now, ctx)) {
            doSuspend(s);
            return Outcome.SUSPENDED;
        }
        return Outcome.NONE;
    }

    /**
     * 到期释放: 删 client + 释放落地机 + 标 EXPIRED, 同一事务原子提交.
     *
     * <p>client 已不存在(之前清理过)视为幂等成功, 仍标 EXPIRED; 其它失败抛出 → 整笔回滚下轮重试, 不泄漏.
     */
    private void doExpire(TradeSubscriptionDO s) {
        String clientId = s.getXrayClientId();
        if (clientId != null) {
            try {
                xrayClientProvisionApi.revoke(clientId);
            } catch (BusinessException be) {
                if (be.getCode() != XrayErrorCode.CLIENT_ENTITY_NOT_FOUND.getCode()) {
                    throw be; // 真失败 → 抛出回滚, 下轮重试
                }
                log.warn("[lifecycle] 到期时 client 已不存在 sub={} client={}, 仍标 EXPIRED", s.getId(), clientId);
            }
        }
        s.setStatus(TradeSubscriptionStatusEnum.EXPIRED.getState());
        tradeSubscriptionMapper.updateById(s);
    }

    /** 用满套餐流量: 停 client (保留 IP/落地机, 远端由 reconcile 摘除) + 订阅置 SUSPENDED. */
    private void doSuspend(TradeSubscriptionDO s) {
        xrayClientProvisionApi.stop(s.getXrayClientId());
        s.setStatus(TradeSubscriptionStatusEnum.SUSPENDED.getState());
        tradeSubscriptionMapper.updateById(s);
    }

    /** 周期重置复活: client 置回 RUNNING (落地机未释放, reconcile 自动装回) + 订阅转 ACTIVE. */
    private void doResume(TradeSubscriptionDO s) {
        xrayClientProvisionApi.resume(s.getXrayClientId());
        s.setStatus(TradeSubscriptionStatusEnum.ACTIVE.getState());
        tradeSubscriptionMapper.updateById(s);
    }
}
