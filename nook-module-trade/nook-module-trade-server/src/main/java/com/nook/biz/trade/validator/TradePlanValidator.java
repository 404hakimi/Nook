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

    /**
     * 校验套餐存在
     *
     * @param id 套餐ID
     * @return 套餐 DO
     */
    public TradePlanDO validateExists(String id) {
        TradePlanDO e = planMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(TradeErrorCode.PLAN_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验套餐存在且 "上架" (下单 / 选址用)
     *
     * @param id 套餐ID
     * @return 套餐 DO
     */
    public TradePlanDO validateEnabled(String id) {
        TradePlanDO e = validateExists(id);
        if (e.getEnabled() == null || e.getEnabled() != 1) {
            throw new BusinessException(TradeErrorCode.PLAN_DISABLED, id);
        }
        return e;
    }

    /**
     * 校验套餐码唯一 (excludeId 非空时排除自身, 用于更新查重)
     *
     * @param code      套餐码
     * @param excludeId 需排除的套餐ID; null 表示新增
     */
    public void validateCodeUnique(String code, String excludeId) {
        boolean dup = excludeId == null
                ? planMapper.existsByCode(code)
                : planMapper.existsByCodeExcludingId(code, excludeId);
        if (dup) {
            throw new BusinessException(TradeErrorCode.PLAN_CODE_DUPLICATE, code);
        }
    }

    /**
     * 校验套餐下是否存在 "生效中" 的订阅 (删套餐前置, 有则拒删)
     *
     * @param id 套餐ID
     */
    public void validateNoActiveSub(String id) {
        if (subscriptionMapper.existsActiveByPlan(id)) {
            throw new BusinessException(TradeErrorCode.PLAN_HAS_ACTIVE_SUB, id);
        }
    }
}
