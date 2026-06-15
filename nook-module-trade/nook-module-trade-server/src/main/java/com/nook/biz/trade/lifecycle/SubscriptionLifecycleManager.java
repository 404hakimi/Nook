package com.nook.biz.trade.lifecycle;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 订阅生命周期流转管理
 *
 * @author nook
 */
@Component
public class SubscriptionLifecycleManager {

    @Resource
    private TradeSubscriptionMapper tradeSubscriptionMapper;
    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 到期释放: 名下凭证全部吊销 (清分配释放落地机, 远端由 agent 对账清理), 订阅转已过期
     *
     * @param sub 订阅
     */
    public void expire(TradeSubscriptionDO sub) {
        // 名下凭证逐个吊销, 落地机随之空闲
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(sub.getId())) {
            tradeSubscriptionCertificateService.revoke(cert.getId());
        }
        this.applyStatus(sub, TradeSubscriptionStatusEnum.EXPIRED);
    }

    /**
     * 退订: 名下凭证全部吊销释放 + 发换机历史事件, 订阅转已取消
     *
     * @param sub 订阅
     */
    public void cancel(TradeSubscriptionDO sub) {
        String operator = StpSystemUtil.getLoginIdOrSystem();
        // 名下凭证逐个吊销释放, 并发布释放事件落审计
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(sub.getId())) {
            String frontlineId = cert.getServerId();
            String landingId = cert.getIpId();
            tradeSubscriptionCertificateService.revoke(cert.getId());
            this.publishRelease(sub, frontlineId, landingId, operator);
        }
        this.applyStatus(sub, TradeSubscriptionStatusEnum.CANCELLED);
    }

    /**
     * 流量耗尽停服: 应运行凭证置应停 (保留落地机占用待恢复), 订阅转已暂停
     *
     * @param sub 订阅
     */
    public void suspend(TradeSubscriptionDO sub) {
        // 仅应运行凭证置应停, 落地机占用不释放
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(sub.getId())) {
            if (TradeCertStatusEnum.ACTIVE.matches(cert.getCertStatus())) {
                tradeSubscriptionCertificateService.updateCertStatus(cert.getId(), TradeCertStatusEnum.SUSPENDED.getState());
            }
        }
        this.applyStatus(sub, TradeSubscriptionStatusEnum.SUSPENDED);
    }

    /**
     * 周期重置恢复: 应停凭证置回应运行 (落地机占用由对账装回), 订阅转生效中
     *
     * @param sub 订阅
     */
    public void resume(TradeSubscriptionDO sub) {
        // 仅应停凭证置回应运行
        for (TradeSubscriptionCertificateDO cert : tradeSubscriptionCertificateService.listBySubscription(sub.getId())) {
            if (TradeCertStatusEnum.SUSPENDED.matches(cert.getCertStatus())) {
                tradeSubscriptionCertificateService.updateCertStatus(cert.getId(), TradeCertStatusEnum.ACTIVE.getState());
            }
        }
        this.applyStatus(sub, TradeSubscriptionStatusEnum.ACTIVE);
    }

    /**
     * 校验流转合法性 (流转表见 {@link TradeSubscriptionStatusEnum}) 后写入订阅状态
     *
     * @param sub    订阅
     * @param target 目标状态
     */
    private void applyStatus(TradeSubscriptionDO sub, TradeSubscriptionStatusEnum target) {
        TradeSubscriptionStatusEnum from = TradeSubscriptionStatusEnum.fromState(sub.getStatus());
        if (ObjectUtil.isNull(from) || !from.canTransitionTo(target)) {
            throw new BusinessException(TradeErrorCode.SUB_INVALID_TRANSITION,
                    ObjectUtil.isNull(from) ? sub.getStatus() : from.getLabel(), target.getLabel());
        }
        sub.setStatus(target.getState());
        tradeSubscriptionMapper.updateById(sub);
    }

    /**
     * 发布凭证释放的换机历史事件 (线路机 / 落地机各一条, 空则跳过)
     *
     * @param sub         订阅
     * @param frontlineId 释放前线路机 id, 可空
     * @param landingId   释放前落地机 id, 可空
     * @param operator    操作者
     */
    private void publishRelease(TradeSubscriptionDO sub, String frontlineId, String landingId, String operator) {
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
}
