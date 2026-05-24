package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * IP 池统计概览 RespVO (顶部 stats 卡片 / 总览面板用)
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSummaryRespVO {

    /** 全量 (含已退役) */
    private Long total;

    // ===== lifecycle 维度 =====
    private Long installing;
    private Long ready;
    private Long live;
    private Long retired;

    // ===== status 维度 (业务占用状态) =====
    private Long available;
    private Long occupied;
    private Long cooling;
    private Long reserved;
}
