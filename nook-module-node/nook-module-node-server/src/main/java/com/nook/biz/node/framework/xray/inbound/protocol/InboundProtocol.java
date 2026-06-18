package com.nook.biz.node.framework.xray.inbound.protocol;

import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;

/**
 * 入站协议实现; 每协议形态封装自己的 装机校验 / 参数生成 / inbound 渲染 (reconcile 加用户的 adu JSON)
 *
 * @author nook
 */
public interface InboundProtocol {

    /**
     * 是否处理该协议 (按基础协议名 vmess / vless 判定)
     *
     * <p>装机侧传 reqVO.inbound.protocol; reconcile 侧传 protocol_key 解出的 protocol.
     *
     * @param protocol 基础协议名 (大小写不敏感)
     * @return 是否匹配
     */
    boolean supports(String protocol);

    /**
     * 协议特定的装机参数校验
     *
     * @param serverId 服务器ID (subdomain 唯一性排除自身)
     * @param reqVO    装机入参
     */
    void validate(String serverId, XrayInstallReqVO reqVO);

    /**
     * 算形态 / 语义参数 / 模板占位符, 并完成协议特定的部署前置 (域名解析 / CF A 记录 / 密钥生成)
     *
     * @param ctx 装机上下文
     * @return 协议产出 (含 fullDomain / cfApiToken)
     */
    InboundProvision provision(InboundProvisionContext ctx);

    /**
     * 渲染 reconcile 加用户用的 adu 入参 JSON: {"inbounds":[{tag,listen,port,protocol,settings:{clients:[...]}}]}.
     *
     * <p>陷阱: xray adu 把整段当一份完整 inbound config 走 validator; 缺 listen/port 会被静默拒
     * (exit 0 + "Added 0 user(s)"). 故填 listen=0.0.0.0 + port=1 占位 (xray 按 tag 匹配运行中的 inbound, 不改真实监听端口).
     *
     * @param tag  共享 inbound tag
     * @param user 加入用户规格 (uuid / email / flow)
     * @return 含顶层 "inbounds" 的完整 JSON
     */
    String buildAduJson(String tag, InboundUserSpec user);
}
