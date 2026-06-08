package com.nook.biz.node.controller.resource.vo.landing;

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
    /** 装机中数量. */
    private long installing;
    /** 待上线数量. */
    private long ready;
    /** 运行中数量. */
    private long live;
    /** 已退役数量. */
    private long retired;

    // ===== 占用维度 (cert.ip_id 派生) =====
    /** 可用数量. */
    private long available;
    /** 已占用数量. */
    private long occupied;
}
