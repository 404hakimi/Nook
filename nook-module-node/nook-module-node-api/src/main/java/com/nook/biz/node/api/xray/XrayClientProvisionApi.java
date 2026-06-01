package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 客户端开通/吊销跨模块契约 (trade 下单 / 退订时调; 内部走 op 编排框架同步执行).
 *
 * @author nook
 */
public interface XrayClientProvisionApi {

    /**
     * 开通客户端 (占用落地机 + 远端 inbound/rule/outbound 三段下发, 同步等终态).
     *
     * @param req 开通入参
     * @return 新建的 xray_client.id
     */
    String provision(XrayClientProvisionDTO req);

    /**
     * 吊销客户端 (远端清理 + 落地机归还 COOLING).
     *
     * @param clientId xray_client.id
     */
    void revoke(String clientId);

    /**
     * 停服客户端 (置 status=STOPPED, 保留 client 记录 + 落地机占用); 流量耗尽暂停用,
     * 续费/重置后可恢复. 远端 user/rule/outbound 由 reconcile 移除, 落地 tc 自动清零.
     *
     * @param clientId xray_client.id
     */
    void stop(String clientId);

    /**
     * 批量统计各线路机活跃 (status=RUNNING) 客户数; allocator 选客户数最少线路机用.
     *
     * @param serverIds 线路机 server id 集合
     * @return serverId → 活跃客户数 (无客户的 server 不在 map 里)
     */
    Map<String, Integer> countActiveByServerIds(Collection<String> serverIds);

    /**
     * 改客户端所在线路机 (故障切换); 只改 server_id, 落地(ip_id)/uuid 不变, 远端由 reconcile 两端收敛.
     *
     * @param clientId    xray_client.id
     * @param newServerId 新线路机 server id
     */
    void rebindFrontline(String clientId, String newServerId);
}
