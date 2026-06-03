package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerPageReqDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.common.web.response.PageResult;

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
     * 校验 server 存在, 返完整 DTO; 不存在抛 BusinessException(SERVER_NOT_FOUND).
     *
     * @param serverId server 主键
     * @return server 视图
     */
    ResourceServerRespDTO validateExists(String serverId);

    /**
     * 按 id 查 server; 不存在返 null. 用于日志增强等可选查询; 需"不存在即报错"用 {@link #validateExists}.
     *
     * @param serverId server 主键
     * @return server 视图; 不存在返 null
     */
    ResourceServerRespDTO getServer(String serverId);

    /**
     * 按 agent_token 查 server; 不存在返 null (调用方自己抛 UNAUTHORIZED).
     *
     * @param agentToken X-Agent-Token header 值
     * @return server 视图; 未匹配返 null
     */
    ResourceServerRespDTO getByAgentToken(String agentToken);

    /**
     * 分页查 server (agent 模块拼 admin 列表用).
     *
     * @param reqDTO 分页 + 筛选
     * @return PageResult; records 元素是 server 视图
     */
    PageResult<ResourceServerRespDTO> page(ResourceServerPageReqDTO reqDTO);

    /**
     * 批量获得服务器名称 (跨模块 enrich op_log 列表用)
     *
     * @param serverIds 服务器编号集合
     * @return 服务器编号 → 服务器名称
     */
    Map<String, String> getServerNameMap(Collection<String> serverIds);

    /**
     * 批量查 server 概要 (含 serverType / lifecycleState / ipAddress; trade 绑定校验 + enrich 用)
     *
     * @param serverIds 服务器编号集合
     * @return server 视图列表 (不存在的 id 跳过)
     */
    List<ResourceServerRespDTO> listByServerIds(Collection<String> serverIds);

    /**
     * 查某区域的 LIVE 线路机 (trade allocator 选线路机用).
     *
     * @param region 区域码
     * @return LIVE frontline 列表
     */
    List<ResourceServerRespDTO> findLiveFrontlinesByRegion(String region);

    /**
     * 从候选 server 里筛出健康可分配的子集 (综合 生命周期 + 流量配额 + 心跳 一处判定).
     *
     * @param serverIds 候选 server 编号
     * @return 可分配的 server 编号子集
     */
    Set<String> filterAllocatable(Collection<String> serverIds);

    /**
     * 查需要故障切换的 LIVE 线路机 (到顶 THROTTLED / 掉线 OFFLINE); trade 故障切换 Job 用.
     *
     * @return serverId → 原因 (THROTTLED / OFFLINE)
     */
    Map<String, String> findFrontlinesNeedingFailover();

    /**
     * 列全部 LIVE server (线路机 + 落地机); agent 心跳监控 Job 用.
     *
     * @return LIVE server 列表
     */
    List<ResourceServerRespDTO> listLive();
}
