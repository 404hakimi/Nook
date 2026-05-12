package com.nook.biz.node.controller.operation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台 - OpType 枚举选项 Resp VO (供前端 Create 下拉用)
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpTypeOptionRespVO {

    /** OpType.name() */
    private String opType;

    /** DB 是否已存在该 opType 的 op_config 行 (前端用此过滤掉已建的) */
    private boolean configured;
}
