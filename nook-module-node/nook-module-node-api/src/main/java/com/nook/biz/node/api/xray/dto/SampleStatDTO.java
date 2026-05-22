package com.nook.biz.node.api.xray.dto;

/** 一次 agent 上报样本入库结果统计. */
public record SampleStatDTO(int upserted, int skipped) {
}
