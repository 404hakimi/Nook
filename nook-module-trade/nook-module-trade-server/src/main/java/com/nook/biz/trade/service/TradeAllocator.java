package com.nook.biz.trade.service;

import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayClientProvisionApi;
import com.nook.biz.trade.api.enums.TradePlanResourceTypeEnum;
import com.nook.biz.trade.dal.dataobject.TradePlanResourceDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源分配器: 从套餐 SKU 池选线路机 / 落地机. 只选址, 实际开通复用 node 的 provision.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class TradeAllocator {

    private static final String LIVE = "LIVE";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String FRONTLINE = TradePlanResourceTypeEnum.FRONTLINE.getType();
    private static final String LANDING = TradePlanResourceTypeEnum.LANDING.getType();

    private final TradePlanResourceMapper resourceMapper;
    private final ResourceServerApi serverApi;
    private final ResourceServerLandingApi landingApi;
    private final XrayClientProvisionApi provisionApi;

    /** 选 SKU 池里客户数最少的 LIVE 线路机; 无候选返 null. */
    public String pickFrontline(String planId) {
        List<String> ids = enabledResourceIds(planId, FRONTLINE);
        if (ids.isEmpty()) {
            return null;
        }
        List<String> liveIds = serverApi.listByServerIds(ids).stream()
                .filter(s -> LIVE.equals(s.getLifecycleState()))
                .map(ResourceServerRespDTO::getId)
                .toList();
        if (liveIds.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = provisionApi.countActiveByServerIds(liveIds);
        return liveIds.stream()
                .min(Comparator.comparingInt(id -> counts.getOrDefault(id, 0)))
                .orElse(null);
    }

    /** 选 SKU 池里 LIVE+AVAILABLE 的落地机 (排除已试过的); 无候选返 null. */
    public String pickLanding(String planId, Set<String> exclude) {
        List<String> ids = enabledResourceIds(planId, LANDING).stream()
                .filter(id -> !exclude.contains(id))
                .toList();
        if (ids.isEmpty()) {
            return null;
        }
        return landingApi.listSummaryByServerIds(ids).stream()
                .filter(s -> LIVE.equals(s.getLifecycleState()) && AVAILABLE.equals(s.getStatus()))
                .map(LandingSummaryDTO::getServerId)
                .findFirst()
                .orElse(null);
    }

    private List<String> enabledResourceIds(String planId, String resourceType) {
        return resourceMapper.selectByPlan(planId, resourceType).stream()
                .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                .map(TradePlanResourceDO::getResourceId)
                .toList();
    }
}
