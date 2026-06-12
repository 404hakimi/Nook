package com.nook.biz.node.api.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 套餐规格 DTO
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanSpecDTO {

    /** 套餐ID; 容量结果按此回填. */
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
