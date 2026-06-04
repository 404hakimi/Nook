package com.nook.biz.node.api.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 套餐规格 DTO (算落地机池容量的入参; 与 trade 解耦, 只描述匹配维度)
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanSpecDTO {

    /** 业务侧 key (planId), 容量结果按此回填. */
    private String planId;

    /** 区域码. */
    private String regionCode;

    /** IP 类型编号. */
    private String ipTypeId;

    /** 套餐月流量 GB. */
    private int trafficGb;

    /** 套餐带宽 Mbps. */
    private int bandwidthMbps;
}
