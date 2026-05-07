-- ============================================================
-- 模块: monitor  观测域
--   - 流量统计   (traffic_snapshot / traffic_daily)
--   - 服务器带宽 (server_bw)
--   - IP 健康检查 (ip_health)
--   - 告警       (alert)
--
-- 现阶段不分区。后续数据量大了再 ALTER TABLE ... PARTITION BY RANGE。
-- ============================================================

-- 流量快照(5分钟粒度，每会员/每IP一行)
CREATE TABLE `monitor_traffic_snapshot` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id` CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `ip_id`          CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `server_id`      CHAR(32)        NOT NULL COMMENT '关联 resource_server.id',
    `snapshot_time`  DATETIME        NOT NULL COMMENT '快照时间(对齐到5分钟)',
    `uplink_bytes`   BIGINT          NOT NULL DEFAULT 0 COMMENT '本周期上行字节',
    `downlink_bytes` BIGINT          NOT NULL DEFAULT 0 COMMENT '本周期下行字节',
    `online_devices` INT             NOT NULL DEFAULT 0 COMMENT '本周期在线设备数',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流量快照表(5分钟粒度)';

-- 流量日报(按会员+IP+日期聚合)
CREATE TABLE `monitor_traffic_daily` (
    `id`                CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id`    CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `ip_id`             CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `log_date`          DATE            NOT NULL COMMENT '统计日期',
    `uplink_bytes`      BIGINT          NOT NULL DEFAULT 0 COMMENT '当日上行字节',
    `downlink_bytes`    BIGINT          NOT NULL DEFAULT 0 COMMENT '当日下行字节',
    `peak_devices`      INT             NOT NULL DEFAULT 0 COMMENT '当日峰值在线设备数',
    `peak_uplink_bps`   BIGINT          NOT NULL DEFAULT 0 COMMENT '当日上行峰值速率',
    `peak_downlink_bps` BIGINT          NOT NULL DEFAULT 0 COMMENT '当日下行峰值速率',
    `created_at`        DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流量日报表';

-- 服务器带宽快照(整机维度，5分钟粒度)
CREATE TABLE `monitor_server_bw` (
    `id`                  CHAR(32)        NOT NULL COMMENT '主键ID',
    `server_id`           CHAR(32)        NOT NULL COMMENT '关联 resource_server.id',
    `snapshot_time`       DATETIME        NOT NULL COMMENT '快照时间(对齐到5分钟)',
    `total_uplink_bps`    BIGINT          NOT NULL DEFAULT 0 COMMENT '服务器上行速率 bps',
    `total_downlink_bps`  BIGINT          NOT NULL DEFAULT 0 COMMENT '服务器下行速率 bps',
    `active_ip_count`     INT             NOT NULL DEFAULT 0 COMMENT '活跃 IP 数',
    `active_member_count` INT             NOT NULL DEFAULT 0 COMMENT '活跃会员数',
    `created_at`          DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器带宽快照表(5分钟粒度)';

-- IP 健康检查日志
CREATE TABLE `monitor_ip_health` (
    `id`         CHAR(32)        NOT NULL COMMENT '主键ID',
    `ip_id`      CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `platform`   VARCHAR(32)     DEFAULT NULL COMMENT '检测平台(空=通用连通性检测)',
    `result`     TINYINT         NOT NULL COMMENT '结果: 1=正常 2=被拦截 3=超时 4=异常',
    `http_code`  INT             DEFAULT NULL COMMENT 'HTTP 响应码',
    `latency_ms` INT             DEFAULT NULL COMMENT '延迟毫秒',
    `detail`     VARCHAR(512)    DEFAULT NULL COMMENT '检测详情',
    `checked_at` DATETIME        NOT NULL COMMENT '检测时间',
    `created_at` DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 健康检查日志表';

-- 系统告警
CREATE TABLE `monitor_alert` (
    `id`          CHAR(32)        NOT NULL COMMENT '主键ID',
    `level`       TINYINT         NOT NULL COMMENT '级别: 1=info 2=warning 3=critical',
    `type`        VARCHAR(64)     NOT NULL COMMENT '告警类型: server_down/ip_blocked_batch/traffic_abnormal',
    `title`       VARCHAR(255)    NOT NULL COMMENT '告警标题',
    `detail`      TEXT            DEFAULT NULL COMMENT '告警详情',
    `is_resolved` TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已处理: 0=未处理 1=已处理',
    `resolved_at` DATETIME        DEFAULT NULL COMMENT '处理时间',
    `created_at`  DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统告警表';
