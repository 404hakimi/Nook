package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 落地机概要查询 Api (跨模块; trade 算 SKU 池容量 + 绑定校验用).
 *
 * @author nook
 */
public interface ResourceServerLandingApi {

    /**
     * 批量查落地机概要
     *
     * @param serverIds 落地机 server id 集合
     * @return List<LandingSummaryDTO>
     */
    List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds);

    /**
     * 查匹配套餐的 LIVE 落地机 (同区域 + 同 IP 类型 + 容量达标)
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型
     * @param minTrafficGb     套餐月流量
     * @param minBandwidthMbps 套餐带宽
     * @return List<LandingSummaryDTO>
     */
    List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                int minTrafficGb, int minBandwidthMbps);

    /**
     * 批量计算套餐落地机池容量
     *
     * @param specs 套餐规格集合
     * @return Map<String, PlanCapacityDTO>
     */
    Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs);
}
