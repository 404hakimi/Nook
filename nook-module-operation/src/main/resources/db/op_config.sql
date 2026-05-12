-- Op 调度配置: 按 opType 一行, admin 在线调整超时 / 启停
-- 行由应用启动期 OpConfigBootstrapper 按 OpType 枚举补齐, 此 DDL 不带 seed
CREATE TABLE IF NOT EXISTS `op_config` (
    `id`                   VARCHAR(64)  NOT NULL COMMENT '主键 (32 位无连字符 UUID, MP ASSIGN_UUID)',
    `op_type`              VARCHAR(64)  NOT NULL COMMENT 'OpType 枚举名',
    `name`                 VARCHAR(64)  NOT NULL COMMENT '中文显示名',
    `exec_timeout_seconds` INT          NOT NULL COMMENT 'handler 执行超时秒数 (watchdog 切线程)',
    `wait_timeout_seconds` INT          NOT NULL COMMENT 'caller 阻塞等待秒数 (submitAndWait)',
    `max_retry`            INT          NOT NULL DEFAULT 0 COMMENT '失败自动重试次数 (保留, 当前未启用)',
    `enabled`              TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用; 关闭后该 op 入队抛错',
    `description`          VARCHAR(255) NULL     COMMENT '备注',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_op_type` (`op_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Op 调度配置';
