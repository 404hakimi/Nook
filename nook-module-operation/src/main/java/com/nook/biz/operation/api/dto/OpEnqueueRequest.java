package com.nook.biz.operation.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 入队入参; 调用方填好 server / opType / target / operator / params 传给 orchestrator.
 *
 * <p>opType 用字符串而非枚举: 框架不绑定具体业务的 op 集合, 各 biz 模块自己定义 enum 然后传 .name();
 * 同一 server 下不同 biz 模块的 op 共享 FIFO 互斥队列.
 *
 * @author nook
 */
@Getter
@Builder
public class OpEnqueueRequest {

    /** 目标 server id */
    private final String serverId;

    /** op 类型 (biz 模块 enum 的 .name(); 也允许任意自定义 String); 决定 handler 路由 + 超时配置查找 */
    private final String opType;

    /** 子资源 id (如 client_id); server 级 op 为 null */
    private final String targetId;

    /** 触发者; 用户操作填 userId, 系统调度填 "SYSTEM" / "SCHEDULER" */
    private final String operator;

    /** 入参 JSON; handler 内可反序列化使用, 也作为 audit 留痕 */
    private final String paramsJson;

    /**
     * 是否允许同三元组并存; 默认 false 走 dedup.
     * <p>典型场景: 新建资源类 op (CLIENT_PROVISION) 没有 targetId, 多个并发请求都应入队 FIFO 跑, 这里设 true.
     */
    @Builder.Default
    private final boolean allowDuplicate = false;
}
