package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerPageReqDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * 按 agent_token 查 server; 不存在返 null (调用方自己抛 UNAUTHORIZED).
     *
     * @param agentToken X-Agent-Token header 值
     * @return server 视图; 未匹配返 null
     */
    ResourceServerRespDTO getByAgentToken(String agentToken);

    /**
     * 拉所有未软删的 server (内部 batch 取名 / 跨表用; UI 列表请用 page).
     *
     * @return server 列表; 空表返空 list
     */
    List<ResourceServerRespDTO> listAll();

    /**
     * 分页查 server (agent 模块拼 admin 列表用).
     *
     * @param reqDTO 分页 + 筛选
     * @return PageResult; records 元素是 server 视图
     */
    PageResult<ResourceServerRespDTO> page(ResourceServerPageReqDTO reqDTO);

    /**
     * 批量获得服务器名称 (跨模块 enrich op_log / agent_task 列表用)
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
}
