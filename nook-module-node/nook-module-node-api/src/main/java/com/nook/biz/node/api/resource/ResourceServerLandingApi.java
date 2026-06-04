package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 落地机概要查询 Api 接口
 *
 * @author nook
 */
public interface ResourceServerLandingApi {

    /**
     * 批量查落地机概要
     *
     * @param serverIds 落地机ID集合
     * @return 落地机概要列表
     */
    List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds);

    /**
     * 查匹配套餐的运行中落地机 (同区域 + 同 IP 类型 + 容量达标)
     *
     * @param region           区域码
     * @param ipTypeId         IP 类型
     * @param minTrafficGb     套餐月流量
     * @param minBandwidthMbps 套餐带宽
     * @return 落地机概要列表
     */
    List<LandingSummaryDTO> findMatchingForPlan(String region, String ipTypeId,
                                                int minTrafficGb, int minBandwidthMbps);

    /**
     * 批量计算套餐落地机池容量
     *
     * @param specs 套餐规格集合
     * @return 套餐ID → 池容量
     */
    Map<String, PlanCapacityDTO> countCapacityForPlans(Collection<PlanSpecDTO> specs);

    /**
     * 查落地机 socks5 端口
     *
     * @param serverId 落地机ID
     * @return socks5 端口 (0 表示未配置或落地机不存在)
     */
    int getSocks5Port(String serverId);
}
