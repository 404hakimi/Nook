package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源服务器 Api 接口
 *
 * @author nook
 */
public interface ResourceServerApi {

    /**
     * 校验服务器存在并返回视图; 不存在则抛业务异常
     *
     * @param serverId 服务器ID
     * @return 服务器视图
     */
    ResourceServerRespDTO validateExists(String serverId);

    /**
     * 按ID查服务器; 不存在返 null (需"不存在即报错"用 {@link #validateExists})
     *
     * @param serverId 服务器ID
     * @return 服务器视图
     */
    ResourceServerRespDTO getServer(String serverId);

    /**
     * 校验 agent 上行鉴权证明并返回服务器 (token 不过线: 按明文 serverId 找 token, 用 token 解密证明, 成功即鉴权)
     *
     * @param serverId  agent 明文上报的 serverId (X-Agent-Server 头)
     * @param authProof agent 用 token 加密的鉴权证明 (X-Agent-Auth 头)
     * @return 服务器视图; 校验失败抛 UNAUTHORIZED
     */
    ResourceServerRespDTO verifyAgentAuth(String serverId, String authProof);

    /**
     * 查某区域运行中的线路机
     *
     * @param region 区域码
     * @return 线路机列表
     */
    List<ResourceServerRespDTO> findLiveFrontlinesByRegion(String region);

    /**
     * 从候选服务器中筛出健康可分配的子集 (综合生命周期 / 流量配额 / 心跳判定)
     *
     * @param serverIds 候选服务器ID集合
     * @return 可分配的服务器ID子集
     */
    Set<String> filterAllocatable(Collection<String> serverIds);

    /**
     * 查需要故障切换的运行中线路机 (已触发限流或掉线)
     *
     * @return 服务器ID → 切换原因
     */
    Map<String, String> findFrontlinesNeedingFailover();

    /**
     * 列出全部运行中服务器 (线路机 + 落地机)
     *
     * @return 运行中服务器列表
     */
    List<ResourceServerRespDTO> listLive();
}
