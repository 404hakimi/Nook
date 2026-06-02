package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * 单个 xray 客户端的期望态 (reconcile 用; agent 拿去跟本地实际 diff + apply).
 *
 * <p>后端把 3 段 xray api JSON 预拼好下发, agent 只按 email/tag diff + 把 JSON 喂给 xray, 不在 Go 侧重复协议拼装。
 *
 * @author nook
 */
@Data
public class XrayReconcileClientDTO {

    /** inbound user 邮箱 (身份键; reconcile 按 email 比对存在性). */
    private String clientEmail;

    /** inbound user 的 UUID; reconcile 比对远端 account.id, 不一致(rotate)则摘旧装新. */
    private String clientUuid;

    /** 共享 inbound tag (rmu 用). */
    private String inboundTag;

    /** 业务 outbound tag = out_&lt;clientId&gt; (按 tag diff; rmo 用). */
    private String outboundTag;

    /** 业务 routing rule tag = rule_&lt;clientId&gt; (按 tag diff; rmrules 用). */
    private String ruleTag;

    /** 预拼: 喂 {@code xray api adu} 的 {"inbounds":[...]} JSON. */
    private String aduJson;

    /** 预拼: 喂 {@code xray api ado} 的 {"outbounds":[...]} JSON. */
    private String adoJson;

    /** 预拼: 喂 {@code xray api adrules --append} 的 {"routing":{"rules":[...]}} JSON. */
    private String adrulesJson;
}
