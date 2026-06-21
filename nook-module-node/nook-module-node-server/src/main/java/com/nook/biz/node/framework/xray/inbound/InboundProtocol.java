package com.nook.biz.node.framework.xray.inbound;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入站协议实现; 每协议形态封装自己的 装机校验 / 参数生成 / inbound 渲染 (reconcile 加用户的 adu JSON)
 *
 * @author nook
 */
public interface InboundProtocol {

    /**
     * 本实现处理的协议形态集 (一个策略可覆盖多形态, 如 vmess 的 ws-tls / ws-plain); 工厂据此建「协议名 → 实现」分派表
     *
     * @return 处理的 {@link XrayInboundProtocolEnum} 形态集
     */
    Set<XrayInboundProtocolEnum> supportedForms();

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
     * @param ctx 开通请求
     * @return 开通结果 (含 fullDomain / cfApiToken)
     */
    InboundProvisionResult provision(InboundProvisionRequest ctx);

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
    String buildAduJson(String tag, InboundUserRequest user);

    /**
     * 重装时算本协议客户面连接参数的变更项 (变了则在用客户需重拉订阅才能恢复)
     *
     * <p>客户面参数是协议特定的 (vmess 看 ws path / 对外域名; vless reality 密钥每次重装必重新生成 → 恒变更),
     * 故各协议自报; 调用方只比通用字段 (端口 / 监听 IP / 协议形态), 协议形态没变才下放到这里.
     *
     * @param existingParams 现存语义参数 (DB 解出, 跟本协议同形态)
     * @param newInput       新装机 inbound 入参
     * @return 客户面变更项的人读描述; 空 = 无变更
     */
    List<String> clientFacingDiff(InboundParams existingParams, XrayInboundConfigVO newInput);

    /**
     * 拼本协议的客户分享链接 (vmess:// / vless://); host (域名 vs 出网 IP) 由协议自定
     *
     * @param params 语义参数 (DB 解出, 跟本协议同形态); 可空 (按缺省渲染)
     * @param ctx    渲染上下文 (出网 IP / 端口 / uuid / 展示名)
     * @return 分享链接; host 拼不出时返回 null (调用方跳过)
     */
    String buildShareLink(InboundParams params, ShareContext ctx);

    /**
     * 拼本协议的 Clash proxy 节点 (vmess ws-opts / vless reality-opts); 字段与分享链接同源
     *
     * @param params 语义参数 (DB 解出, 跟本协议同形态); 可空 (按缺省渲染)
     * @param ctx    渲染上下文 (出网 IP / 端口 / uuid / 展示名)
     * @return Clash proxy 节点 (调用方直接序列化进 YAML); host 拼不出时返回 null
     */
    Map<String, Object> buildClashProxy(InboundParams params, ShareContext ctx);
}
