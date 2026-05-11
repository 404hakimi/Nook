package com.nook.biz.operation.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.operation.api.OpStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * op_log 单条 VO; controller 出参用.
 *
 * @author nook
 */
@Data
public class OpLogRespVO {

    private String id;

    private String serverId;

    /** biz 模块 OpType.name() 字符串; 前端按字面值匹配 TS 联合类型 */
    private String opType;

    private String targetId;

    private String operator;

    private OpStatus status;

    private String currentStep;

    private Integer progressPct;

    private String lastMessage;

    private String errorCode;

    private String errorMsg;

    /** 入参 JSON; 详情才返, 列表里太占字段不返 */
    private String paramsJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enqueuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endedAt;

    /** 执行耗时毫秒 (started_at → ended_at); 未结束为 null */
    private Long elapsedMs;
}
