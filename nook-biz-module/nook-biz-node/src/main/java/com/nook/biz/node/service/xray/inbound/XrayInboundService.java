package com.nook.biz.node.service.xray.inbound;

import com.nook.biz.node.controller.xray.inbound.vo.InboundSnapshotRespVO;

import java.util.List;

/** Xray 入站列表查询; 走 SSH cat xray.json + JSON 解析 (gRPC 没有原生 list-inbound). */
public interface XrayInboundService {

    /** 列指定 server 的所有 inbound (跳过 api 通道). */
    List<InboundSnapshotRespVO> listInbounds(String serverId);
}
