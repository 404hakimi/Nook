package com.nook.biz.resource.api;

import com.nook.biz.resource.api.dto.IpPoolEntryDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Resource 模块对外暴露的 IP 池接口。
 * 跨模块依赖只允许 import 这个 api 包下的类型；不许碰 service / mapper / entity。
 *
 * 流程提示：
 *   - 兑换: pickAvailable(region, ipTypeId, memberUserId) → 返回 IP 凭据；同时 IP 状态从 available → occupied。
 *   - 退订: releaseToCooling(ipId) → IP 状态 occupied → cooling, 一段时间后由 sweep 任务回到 available。
 */
public interface ResourceIpPoolApi {

    /**
     * 按指定 ipId 原子占用 (provision 走前端选定 IP 的路径用); IP 当前必须是 available, 否则抛 IP_POOL_NOT_AVAILABLE.
     * 与 pickAvailable 区别: 后者按 region/type 自动挑, 这里指定 id; 都走 markOccupied(WHERE status=1) 防双卖.
     *
     * @param ipId         IP 池主键
     * @param memberUserId 占用方会员
     * @return IpPoolEntryDTO 含 socks5 凭据 (替代 loadEntry 路径, 一次调用兼任查询和占用)
     */
    IpPoolEntryDTO occupyById(String ipId, String memberUserId);

    /** 退订: 标记 cooling, 默认 30 分钟后由调度回到 available。 */
    void releaseToCooling(String ipId);

    /** 按 id 拿凭据(SOCKS5 用户密码)；不存在抛 IP_POOL_NOT_FOUND。 */
    IpPoolEntryDTO loadEntry(String ipId);

    /**
     * 批量按 id 取 ip_address; 用于列表页 enrich (xray client / 套餐订单 等需要把裸 IP ID 翻成可读地址).
     * 不下发 SOCKS5 凭据 (vs loadEntry), 出参限定到 ipAddress; 缺失的 id 直接不进结果 map.
     *
     * @param ipIds 要查的 ip 主键集合; 空集合 / null 直接返空 map, 不报错
     * @return Map&lt;ipId, ipAddress&gt;; 已删 / 不存在的 id 不会出现在 map 里
     */
    Map<String, String> loadIpAddressMap(Collection<String> ipIds);
}
