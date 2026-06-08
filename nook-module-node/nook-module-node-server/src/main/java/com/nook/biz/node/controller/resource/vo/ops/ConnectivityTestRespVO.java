package com.nook.biz.node.controller.resource.vo.ops;

import lombok.Data;

/**
 * 管理后台 - 连通性测试 Response VO
 *
 * @author nook
 */
@Data
public class ConnectivityTestRespVO {

    /** 是否连通成功. */
    private boolean success;

    /** 探活耗时毫秒，仅 success=true 时有意义 */
    private long elapsedMs;

    /** 失败时填错误描述；成功留空 */
    private String error;
}
