package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * 单个 xray 客户端期望态 DTO; 含后端预拼好的 xray api JSON, agent 比对差异后直接应用, 不在 agent 侧重复拼协议
 *
 * @author nook
 */
@Data
public class XrayReconcileClientDTO {

    /** inbound 用户邮箱; 对账身份键, 按它比对存在性. */
    private String clientEmail;

    /** inbound 用户 UUID; 与远端不一致 (已轮换) 则摘旧装新. */
    private String clientUuid;

    /** 共享 inbound 标签 (删用户用). */
    private String inboundTag;

    /** 该接入点专属 outbound 标签 (按标签比对; 删出站用). */
    private String outboundTag;

    /** 该接入点专属路由规则标签 (按标签比对; 删路由用). */
    private String ruleTag;

    /** 预拼: 喂 {@code xray api adu} 的 {"inbounds":[...]} JSON. */
    private String aduJson;

    /** 预拼: 喂 {@code xray api ado} 的 {"outbounds":[...]} JSON. */
    private String adoJson;

    /** 预拼: 喂 {@code xray api adrules --append} 的 {"routing":{"rules":[...]}} JSON. */
    private String adrulesJson;
}
