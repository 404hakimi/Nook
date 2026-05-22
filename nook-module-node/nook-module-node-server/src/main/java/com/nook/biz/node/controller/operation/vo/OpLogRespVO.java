package com.nook.biz.node.controller.operation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.operation.api.OpStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * op_log Response VO.
 *
 * @author nook
 */
@Data
public class OpLogRespVO {

    private String id;

    private String serverId;

    /** 服务器友好名 (resource_server.name); convert 层回填, 缺失时前端用 serverId 兜底 */
    private String serverName;

    /** biz 模块 OpType.name() 字符串; 前端按字面值匹配 TS 联合类型 */
    private String opType;

    private String targetId;

    /** 操作目标友好名 (xray_client.client_email / 其他业务自定义); convert 层回填 */
    private String targetName;

    /** 触发者 ID (system_user.id; 系统调度填 "SCHEDULER" / "SYSTEM") */
    private String operator;

    /** 触发者友好名 (system_user.real_name / username); 系统占位 operator 此处直接是占位字面值 */
    private String operatorName;

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
