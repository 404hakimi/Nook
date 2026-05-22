package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;

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
}
