package com.nook.biz.trade.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.biz.trade.service.TradeTrafficMeteringService;
import com.nook.biz.trade.service.TradeTrafficMeteringService.MeteringContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅生命周期定时任务: 到期释放 + 流量耗尽停服 + 周期重置恢复.
 *
 * <p>每轮以订阅为单位、各一个事务; 流量计量委托 {@link TradeTrafficMeteringService}, 本类管调度 + 生命周期动作(吊销/停服/复活).
 *
 * @author nook
 */
@Slf4j
@Component
public class TradeLifecycleJob {

    @Resource
    private TradeSubscriptionMapper tradeSubscriptionMapper;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private ResourceServerLandingApi resourceServerLandingApi;
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
                // 每订阅一个事务: 计量写 trade_subscription_traffic/额度 与生命周期改状态 原子提交; 任一失败整笔回滚下轮重试
                Outcome o = transactionTemplate.execute(st -> {
                    // 到期 → 释放: 名下凭证先释放落地机再吊销(清分配, 远端 xray 由 agent 对账清理) + 标过期; 重复执行幂等
                    if (ObjectUtil.isNotNull(s.getExpiresAt()) && !s.getExpiresAt().isAfter(now)) {
                        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(s.getId())) {
                            if (ObjectUtil.isNotNull(cert.getIpId())) {
                                resourceServerLandingApi.releaseLanding(cert.getIpId());
                            }
                            tradeSubscriptionCertificateService.revoke(cert.getId());
                        }
                        s.setStatus(TradeSubscriptionStatusEnum.EXPIRED.getState());
                        tradeSubscriptionMapper.updateById(s);
                        return Outcome.EXPIRED;
                    }
                    // 停服订阅: 到重置点才翻篇复活(停服凭证置回应运行, 落地机未释放由对账装回); 没到点继续停
                    if (TradeSubscriptionStatusEnum.SUSPENDED.matches(s.getStatus())) {
                        if (!tradeTrafficMeteringService.tryCycleReset(s, now, ctx)) {
                            return Outcome.NONE;
                        }
                        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(s.getId())) {
                            if (TradeCertStatusEnum.SUSPENDED.matches(cert.getCertStatus())) {
                                tradeSubscriptionCertificateService.updateCertStatus(cert.getId(), TradeCertStatusEnum.ACTIVE.getState());
                            }
                        }
                        s.setStatus(TradeSubscriptionStatusEnum.ACTIVE.getState());
                        tradeSubscriptionMapper.updateById(s);
                        return Outcome.RESUMED;
                    }
                    // 生效中: 累加业务流量; 未到上限继续; 达上限 → 应运行凭证置应停(保留落地机占用) + 订阅停服
                    if (!tradeTrafficMeteringService.accumulate(s, now, ctx)) {
                        return Outcome.NONE;
                    }
                    for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(s.getId())) {
                        if (TradeCertStatusEnum.ACTIVE.matches(cert.getCertStatus())) {
                            tradeSubscriptionCertificateService.updateCertStatus(cert.getId(), TradeCertStatusEnum.SUSPENDED.getState());
                        }
                    }
                    s.setStatus(TradeSubscriptionStatusEnum.SUSPENDED.getState());
                    tradeSubscriptionMapper.updateById(s);
                    return Outcome.SUSPENDED;
                });
                if (o == Outcome.EXPIRED) {
                    expired++;
                } else if (o == Outcome.SUSPENDED) {
                    suspended++;
                } else if (o == Outcome.RESUMED) {
                    resumed++;
                }
            } catch (Exception e) {
                log.error("[lifecycle] sub={} 处理失败: {}", s.getId(), e.getMessage(), e);
            }
        }
        if (expired + suspended + resumed > 0) {
            log.info("[lifecycle] 扫描完成: 总={} 到期释放={} 流量耗尽停服={} 重置恢复={}",
                    subs.size(), expired, suspended, resumed);
        }
    }
}
