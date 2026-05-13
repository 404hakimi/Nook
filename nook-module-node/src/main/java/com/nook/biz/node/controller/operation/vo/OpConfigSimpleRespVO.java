package com.nook.biz.node.controller.operation.vo;

import lombok.Data;

/**
 * 管理后台 - Op 配置精简 Resp VO (用于下拉选择 / 名称回填)
 *
 * @author nook
 */
@Data
public class OpConfigSimpleRespVO {

    private String opType;

    private String name;
}
