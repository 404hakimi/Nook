package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;

import java.util.function.Consumer;

/**
 * 服务器通用 OS 调优运维 Service 接口
 *
 * <p>swap / bbr 等独立触发, 跟 xray install 解耦.
 *
 * @author nook
 */
public interface ResourceServerOpsService {

    /**
     * 启用 swap; 已有 swap 时脚本内幂等跳过, 不重复创建.
     *
     * @param serverId resource_server.id
     * @param reqVO    入参 (sizeMb)
     * @param lineSink 流式 stdout 行回调
     */
    void enableSwap(String serverId, EnableSwapReqVO reqVO, Consumer<String> lineSink);

    /**
     * 启用 BBR 拥塞控制; 内核不支持时 warn 跳过, 不抛错.
     *
     * @param serverId resource_server.id
     * @param lineSink 流式 stdout 行回调
     */
    void enableBbr(String serverId, Consumer<String> lineSink);
}
