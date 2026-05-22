package com.nook.biz.node.controller.operation.vo;

import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OpType;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * op_log 分页查询 Request VO.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpLogPageReqVO extends PageParam {

    /** 状态过滤; 留空 = 全部 */
    private OpStatus status;

    /** server 过滤; 留空 = 全部 */
    private String serverId;

    /** 操作类型过滤; 留空 = 全部 */
    private OpType opType;
}
