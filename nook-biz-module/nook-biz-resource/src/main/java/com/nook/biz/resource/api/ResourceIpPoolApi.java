package com.nook.biz.resource.api;

import com.nook.biz.resource.api.dto.IpPoolEntryDTO;

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
     * 选一个可分配的 IP 并原子占用。
     * 池子无货抛 IP_POOL_EXHAUSTED；并发抢占多次失败抛 IP_POOL_OCCUPY_CONFLICT。
     */
    IpPoolEntryDTO pickAvailable(String region, String ipTypeId, String memberUserId);

    /** 退订: 标记 cooling, 默认 30 分钟后由调度回到 available。 */
    void releaseToCooling(String ipId);

    /** 按 id 拿凭据(SOCKS5 用户密码)；不存在抛 IP_POOL_NOT_FOUND。 */
    IpPoolEntryDTO loadEntry(String ipId);
}
