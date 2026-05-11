-- 危险操作流水 (排队 + 执行 + 终态)
-- active_key 仅在 QUEUED / RUNNING 时填值, 终态置 NULL; UNIQUE 索引保证同 (server, opType, target) 在活跃期最多 1 条
CREATE TABLE IF NOT EXISTS `op_log` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键 (32 位无连字符 UUID, MP ASSIGN_UUID 生成)',
    `server_id`     VARCHAR(64)  NOT NULL COMMENT 'resource_server.id',
    `op_type`       VARCHAR(64)  NOT NULL COMMENT 'OpType 枚举名',
    `target_id`     VARCHAR(64)  NULL     COMMENT 'op 针对的子资源 id (如 client_id); server 级 op 为 NULL',
    `operator`      VARCHAR(64)  NULL     COMMENT '触发者: userId / SYSTEM / SCHEDULER',
    `params_json`   TEXT         NULL     COMMENT '入参 JSON',
    `status`        VARCHAR(16)  NOT NULL COMMENT 'QUEUED/RUNNING/DONE/FAILED/CANCELLED/TIMED_OUT',
    `active_key`    VARCHAR(160) NULL     COMMENT '活跃唯一键 = server_id|op_type|target_id; 终态置 NULL',
    `current_step`  VARCHAR(128) NULL     COMMENT '当前步骤描述',
    `progress_pct`  TINYINT      NULL     COMMENT '进度 0-100',
    `last_message`  VARCHAR(512) NULL     COMMENT '最近一条进度消息',
    `error_code`    VARCHAR(32)  NULL     COMMENT '失败错误码',
    `error_msg`     TEXT         NULL     COMMENT '失败原因',
    `enqueued_at`   DATETIME     NOT NULL COMMENT '入队时间',
    `started_at`    DATETIME     NULL     COMMENT '开始执行时间',
    `ended_at`      DATETIME     NULL     COMMENT '终止时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_active` (`active_key`),
    KEY `idx_server_status` (`server_id`, `status`, `enqueued_at`),
    KEY `idx_status_time`   (`status`, `enqueued_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '危险操作流水';
