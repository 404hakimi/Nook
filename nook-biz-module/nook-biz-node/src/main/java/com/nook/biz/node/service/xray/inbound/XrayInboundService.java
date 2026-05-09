package com.nook.biz.node.service.xray.inbound;

import com.nook.biz.node.controller.xray.inbound.vo.InboundSnapshotRespVO;

import java.util.List;

/**
 * Xray 入站列表查询, 走 SSH cat xray.json + JSON 解析 (gRPC 没有原生 list-inbound).
 *
 * @author nook
 */
public interface XrayInboundService {

    /**
     * 列指定 server 的所有 inbound (跳过 nook 自管的 api 通道).
     *
     * @param serverId resource_server.id
     * @return List of InboundSnapshotRespVO
     */
    List<InboundSnapshotRespVO> listInbounds(String serverId);
}
