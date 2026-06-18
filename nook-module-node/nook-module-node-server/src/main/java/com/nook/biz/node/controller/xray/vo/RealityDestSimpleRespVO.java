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

    /** 候选值 = 偷取目标主机名 (如 www.bing.com); 前端也可自定义输入, 装机时回传. */
    private String value;

    /** 展示标签. */
    private String label;
}
