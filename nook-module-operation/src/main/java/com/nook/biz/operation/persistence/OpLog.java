package com.nook.biz.operation.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.framework.mybatis.entity.BaseEntity;
import com.nook.biz.operation.api.OpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * op_log 表实体; id / createdAt / updatedAt 由 {@link BaseEntity} 提供 (MP ASSIGN_UUID + MetaObjectHandler).
 *
 * @author nook
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("op_log")
public class OpLog extends BaseEntity {

    private String serverId;

    /** biz 模块 OpType 枚举的 .name() 字符串 */
    private String opType;

    private String targetId;

    private String operator;

    private String paramsJson;

    private OpStatus status;

    /** 活跃唯一键: server_id|op_type|target_id; 终态置 NULL 让同三元组可重入 */
    private String activeKey;

    private String currentStep;

    private Integer progressPct;

    private String lastMessage;

    private String errorCode;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enqueuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endedAt;
}
