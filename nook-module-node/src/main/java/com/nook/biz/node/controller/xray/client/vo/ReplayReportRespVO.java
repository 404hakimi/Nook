package com.nook.biz.node.controller.xray.client.vo;

import lombok.Data;

import java.util.List;

/**
 * server 全量 replay 报告; replayServer 出参.
 *
 * @author nook
 */
@Data
public class ReplayReportRespVO {

    /** 关联 resource_server.id. */
    private String serverId;

    /** DB 里 status≠2 的 client 总数. */
    private int totalCount;

    /** 远端已对齐, 跳过未推 (避免无谓断连); 不算入 successCount. */
    private int alreadyOkCount;

    /** 实际推成功数 (远端缺失 → addInbound + addSocksOutbound). */
    private int successCount;

    /** 推失败的 client id 列表; 这些行已被标 status=3, 等下一轮 reconciler 重试. */
    private List<String> failedClientIds;
}
