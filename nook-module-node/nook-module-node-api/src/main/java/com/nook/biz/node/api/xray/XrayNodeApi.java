package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 节点 Api 接口
 *
 * @author nook
 */
public interface XrayNodeApi {

    /**
     * 获取 server 的 xray 部署信息.
     *
     * @param serverId server 主键
     * @return xray 信息 DTO; 该 server 未装 xray 返 null (agent install meta + frontline 装机校验都靠这个)
     */
    XrayNodeRespDTO getByServerId(String serverId);

    /**
     * 批量取 serverIds → XrayNodeRespDTO 映射; 未装 xray 的 serverId 不在 map 里 (调用方 get 返 null 即视为未装).
     */
    Map<String, XrayNodeRespDTO> listByServerIds(Collection<String> serverIds);
}
