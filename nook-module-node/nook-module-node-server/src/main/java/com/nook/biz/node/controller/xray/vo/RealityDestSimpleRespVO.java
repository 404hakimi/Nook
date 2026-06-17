package com.nook.biz.node.controller.xray.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理后台 - REALITY dest 候选 Simple Response VO
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class RealityDestSimpleRespVO {

    /** 候选值 (RealityDestPreset name, 装机时回传). */
    private String value;

    /** 展示标签. */
    private String label;
}
