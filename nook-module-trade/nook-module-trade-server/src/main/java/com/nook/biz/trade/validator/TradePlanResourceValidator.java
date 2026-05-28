package com.nook.biz.trade.validator;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.trade.api.enums.TradeErrorCode;
import com.nook.biz.trade.api.enums.TradePlanResourceTypeEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanResourceMapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 套餐资源绑定校验.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class TradePlanResourceValidator {

    private static final String LIVE = "LIVE";
    private static final String FRONTLINE = "frontline";

    private final TradePlanResourceMapper resourceMapper;
    private final ResourceServerApi serverApi;
    private final ResourceServerLandingApi landingApi;

    /** 绑定校验: 类型合法 + 去重 + 资源 LIVE + (landing) ip_type 匹配套餐. */
    public void validateBind(TradePlanDO plan, String resourceType, String resourceId) {
        TradePlanResourceTypeEnum type = TradePlanResourceTypeEnum.fromType(resourceType);
        if (type == null) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "未知 resourceType: " + resourceType);
        }
        if (resourceMapper.existsByPlanAndResource(plan.getId(), resourceType, resourceId)) {
            throw new BusinessException(TradeErrorCode.PLAN_RESOURCE_DUPLICATE);
        }
        if (type == TradePlanResourceTypeEnum.FRONTLINE) {
            ResourceServerRespDTO srv = serverApi.validateExists(resourceId);
            if (!FRONTLINE.equals(srv.getServerType()) || !LIVE.equals(srv.getLifecycleState())) {
                throw new BusinessException(TradeErrorCode.PLAN_RESOURCE_NOT_LIVE, resourceId);
            }
        } else {
            serverApi.validateExists(resourceId);
            List<LandingSummaryDTO> list = landingApi.listSummaryByServerIds(List.of(resourceId));
            if (list.isEmpty() || !LIVE.equals(list.get(0).getLifecycleState())) {
                throw new BusinessException(TradeErrorCode.PLAN_RESOURCE_NOT_LIVE, resourceId);
            }
            if (StrUtil.isNotBlank(plan.getIpTypeId())
                    && !plan.getIpTypeId().equals(list.get(0).getIpTypeId())) {
                throw new BusinessException(TradeErrorCode.PLAN_RESOURCE_TYPE_MISMATCH);
            }
        }
    }
}
