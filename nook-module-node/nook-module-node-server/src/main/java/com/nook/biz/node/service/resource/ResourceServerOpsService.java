package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;

import java.util.function.Consumer;

/**
 * 服务器通用运维 Service 接口
 *
 * @author nook
 */
public interface ResourceServerOpsService {

    /**
     * 启用 swap 分区
     *
     * @param serverId 服务器编号
     * @param reqVO    swap 入参
     * @param lineSink 每行 stdout 的消费回调
     */
    void enableSwap(String serverId, EnableSwapReqVO reqVO, Consumer<String> lineSink);

    /**
     * 启用 BBR 拥塞控制
     *
     * @param serverId 服务器编号
     * @param lineSink 每行 stdout 的消费回调
     */
    void enableBbr(String serverId, Consumer<String> lineSink);
}
