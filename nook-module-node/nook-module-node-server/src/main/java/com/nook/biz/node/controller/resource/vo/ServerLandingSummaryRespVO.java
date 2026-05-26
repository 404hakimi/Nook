package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点总览统计 Response VO
 *
 * @author nook
 */
@Data
public class ServerLandingSummaryRespVO {

    /** 总数 (server_type='landing' AND deleted=0). */
    private long total;

    // ===== lifecycle 维度 =====
    private long installing;
    private long ready;
    private long live;
    private long retired;

    // ===== 占用状态维度 =====
    private long available;
    private long occupied;
    private long cooling;
    private long reserved;
}
