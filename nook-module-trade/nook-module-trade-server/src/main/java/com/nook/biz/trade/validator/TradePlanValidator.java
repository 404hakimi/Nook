package com.nook.biz.trade.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 套餐业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class TradePlanValidator {

    private final TradePlanMapper planMapper;
    private final TradeSubscriptionMapper subscriptionMapper;

    /** 校验套餐存在. */
    public TradePlanDO validateExists(String id) {
        TradePlanDO e = planMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(TradeErrorCode.PLAN_NOT_FOUND, id);
        }
        return e;
    }

    /** 校验存在且上架 (下单 / allocator 用). */
    public TradePlanDO validateEnabled(String id) {
        TradePlanDO e = validateExists(id);
        if (e.getEnabled() == null || e.getEnabled() != 1) {
            throw new BusinessException(TradeErrorCode.PLAN_DISABLED, id);
        }
        return e;
    }

    /** 套餐码唯一 (excludeId 为 null = Create, 否则 Update 排除自身). */
    public void validateCodeUnique(String code, String excludeId) {
        boolean dup = excludeId == null
                ? planMapper.existsByCode(code)
                : planMapper.existsByCodeExcludingId(code, excludeId);
        if (dup) {
            throw new BusinessException(TradeErrorCode.PLAN_CODE_DUPLICATE, code);
        }
    }

    /** 删套餐前: 不能还有 ACTIVE 订阅. */
    public void validateNoActiveSub(String id) {
        if (subscriptionMapper.existsActiveByPlan(id)) {
            throw new BusinessException(TradeErrorCode.PLAN_HAS_ACTIVE_SUB, id);
        }
    }
}
