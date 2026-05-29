package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;

import java.util.Collection;
import java.util.List;

/**
 * 落地机概要查询 Api (跨模块; trade 算 SKU 池容量 + 绑定校验用).
 *
 * @author nook
 */
public interface ResourceServerLandingApi {

    /**
     * 批量查落地机概要 (主表 lifecycle + landing 子表 status/ipType/ipAddress).
     *
     * @param serverIds 落地机 server id 集合
     * @return 概要列表 (不存在的 id 跳过)
     */
    List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds);

    /**
     * 查匹配套餐的 LIVE 落地机 (同区域 + 同 IP 类型 + 容量达标; 任意 status, 带 status 供算容量/挑机).
     * 落地机 monthly_traffic_gb / bandwidth_limit_mbps 为 0/null = 不限, 跳过该项判定。
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型
     * @param minTrafficGb     套餐月流量 (落地机配额须 ≥, 0/null=不限)
     * @param minBandwidthMbps 套餐带宽 (落地机带宽须 ≥, 0/null=不限)
     * @return 匹配的 LIVE 落地机概要 (含 status)
     */
    List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                int minTrafficGb, int minBandwidthMbps);

    /**
     * 算套餐的落地机池容量 (匹配后按 status 分桶); trade 套餐列表/详情 enrich 用.
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型
     * @param minTrafficGb     套餐月流量
     * @param minBandwidthMbps 套餐带宽
     * @return total / available / occupied
     */
    PlanCapacityDTO countCapacityForPlan(String region, String ipTypeId,
                                         int minTrafficGb, int minBandwidthMbps);
}
