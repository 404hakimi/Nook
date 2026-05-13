package com.nook.biz.node.service.xray.client;

/**
 * 流量采样单次统计; 给 Job 聚合多 server 汇总日志用
 *
 * @param upserted 实际落库的 client 数 (含 INSERT + UPDATE 累加)
 * @param skipped  远端有 DB 无的孤儿 counter 数
 *
 * @author nook
 */
public record SampleStat(int upserted, int skipped) {

    public static final SampleStat EMPTY = new SampleStat(0, 0);
}
