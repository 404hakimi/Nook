package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

import java.util.List;

/**
 * server 与 DB 对账结果 —— 三维度展开: inbound user / outbound / routing rule.
 *
 * <p>tag 形态: outbound = clientId (32-char UUID), rule = "rule_" + clientId, user = email.
 *
 * @author nook
 */
@Data
public class XrayClientSyncStatusRespVO {

    /** 关联 resource_server.id. */
    private String serverId;

    /** 是否能 SSH 连通 + xray api 可读. */
    private boolean reachable;

    /** 共享 inbound 上 user email: DB + 远端均有. */
    private List<String> okEmails;

    /** 共享 inbound 上 user email: DB 有 / 远端缺 (客户连不上, 推 sync 修). */
    private List<String> staleDbEmails;

    /** 共享 inbound 上 user email: 远端有 / DB 没 (孤儿 user, 不自动清). */
    private List<String> orphanRemoteEmails;

    /** clientId: DB 有但远端没对应 socks outbound (流量进 blackhole 兜底被丢, 推 sync 修). */
    private List<String> staleDbOutbounds;

    /** clientId: 远端有 socks outbound 但 DB 没对应 client (孤儿出站, 不自动清). */
    private List<String> orphanRemoteOutbounds;

    /** clientId: DB 有 client 但远端 rule_<clientId> 缺. */
    private List<String> staleDbRules;

    /** clientId: 远端有 rule_<clientId> 但 DB 没对应 client. */
    private List<String> orphanRemoteRules;
}
