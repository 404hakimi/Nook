package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.ShareRenderReqDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 客户分享产物渲染 Api; 协议特定渲染 (vless://vmess:// 链接 / Clash proxy) 收口 node 协议实现, 调用方只编排
 *
 * @author nook
 */
public interface XrayShareApi {

    /**
     * 批量渲染分享链接 (vmess:// / vless://); 拼不出连接 (未装 xray / 无 host) 的 reqKey 不在结果里
     *
     * @param reqs 渲染请求 (reqKey / serverId / uuid / label)
     * @return reqKey → 分享链接
     */
    Map<String, String> renderShareLinks(Collection<ShareRenderReqDTO> reqs);

    /**
     * 批量渲染 Clash proxy 节点 (协议特定结构: vmess ws-opts / vless reality-opts); 调用方直接序列化进 YAML
     *
     * @param reqs 渲染请求 (reqKey / serverId / uuid / label)
     * @return reqKey → Clash proxy 节点 (字段已按协议拼好)
     */
    Map<String, Map<String, Object>> renderClashProxies(Collection<ShareRenderReqDTO> reqs);
}
