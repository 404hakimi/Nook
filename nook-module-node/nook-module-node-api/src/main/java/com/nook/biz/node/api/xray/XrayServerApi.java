package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayServerRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例 Api 接口 (跨模块对外契约)
 *
 * @author nook
 */
public interface XrayServerApi {

    /**
     * 获取 server 的 xray 实例元数据
     *
     * @param serverId server 主键
     * @return xray 实例 DTO; 该 server 未装 xray 返 null
     */
    XrayServerRespDTO getByServerId(String serverId);

    /**
     * 批量取 serverIds → XrayServerRespDTO 映射; 未装 xray 的 serverId 不在 map 里
     *
     * @param serverIds id 集合
     * @return serverId → DTO 映射
     */
    Map<String, XrayServerRespDTO> listByServerIds(Collection<String> serverIds);
}
