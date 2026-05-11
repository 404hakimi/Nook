package com.nook.biz.node.resource.service;

import com.nook.biz.node.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.resource.entity.ResourceIpPool;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * IP 池管理。
 * 状态机:
 *   available(1) ──兑换──▶ occupied(2) ──退订──▶ cooling(5) ──冷却到期──▶ available(1)
 *   人工干预: testing(3) / blacklisted(4) / degraded(6)
 *
 * 兑换/退订相关原子操作(防双卖) 走 Mapper 的 markOccupied / markCooling / markAvailable.
 */
public interface ResourceIpPoolService {

    ResourceIpPool findById(String id);

    PageResult<ResourceIpPool> page(ResourceIpPoolPageReqVO reqVO);

    ResourceIpPool create(ResourceIpPoolSaveReqVO reqVO);

    /** 更新; socks5Password 留空 = 保留旧值, 传值覆盖。 */
    void update(String id, ResourceIpPoolSaveReqVO reqVO);

    void delete(String id);

    /**
     * 选一个可分配的 IP 并原子占用; 返回 null 表示池子里没货。
     * 使用 selectAvailable + markOccupied(WHERE status=1) 防双卖, 失败则重试一次。
     */
    ResourceIpPool occupyOne(String region, String ipTypeId, String memberUserId);

    /**
     * 按指定 ipId 原子占用; markOccupied 0 行受影响时 (IP 当前不是 available) 抛 IP_POOL_NOT_AVAILABLE.
     */
    ResourceIpPool occupyById(String id, String memberUserId);

    /** 退订: 标记为 cooling, 设冷却到期时间(默认 30 分钟后)。 */
    void releaseToCooling(String id);

    /** 冷却到期批量回到 available; 调度器 / 定时任务调用。返回处理条数。 */
    int sweepExpiredCooling();

    /**
     * 批量按 id 取 ip_address (list 页 enrich 用); 走 selectBatchIds 避免 N+1.
     * 不下发 socks5 凭据; 缺失的 id 直接不进结果 map.
     */
    Map<String, String> loadIpAddressMap(Collection<String> ipIds);
}
