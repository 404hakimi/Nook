package com.nook.biz.node.controller.xray.client.vo;

import lombok.Data;

import java.util.List;

/**
 * server 与 DB 对账结果; reconciler 出参.
 *
 * @author nook
 */
@Data
public class SyncStatusRespVO {

    /** 关联 resource_server.id. */
    private String serverId;

    /** 是否能 SSH 连通 + xray 跑着. */
    private boolean reachable;

    /** DB 与远端均有 (in_slot_NN tag 双方都在), 视为 OK. */
    private List<String> okTags;

    /** DB 有但远端缺失的 tag; 需要 replay 推回去. */
    private List<String> staleDbTags;

    /** 远端有但 DB 没有的 tag (排除静态预置 api / freedom 占位); 视为孤儿, 可调 rmi 删. */
    private List<String> orphanRemoteTags;
}
