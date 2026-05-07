-- ============================================================
-- 模块: xray  Xray 内核对接
-- ============================================================

-- Xray inbound 配置(每个独享IP用户对应一个 inbound)
CREATE TABLE `xray_inbound` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `server_id`      CHAR(32)        NOT NULL COMMENT '关联 resource_server.id',
    `member_user_id` CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `ip_id`          CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `inbound_tag`    VARCHAR(128)    NOT NULL COMMENT 'inbound 标签 格式: member_{memberUserId}_{ipId}',
    `listen_ip`      VARCHAR(45)     NOT NULL COMMENT 'inbound 监听IP',
    `listen_port`    INT             NOT NULL COMMENT 'inbound 监听端口',
    `protocol`       VARCHAR(16)     NOT NULL DEFAULT 'vless' COMMENT '协议',
    `uuid`           VARCHAR(36)     NOT NULL COMMENT 'VLESS UUID',
    `transport`      VARCHAR(32)     NOT NULL DEFAULT 'reality' COMMENT '传输方式: reality/ws/tcp',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=运行 2=已停止 3=待同步',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Xray Inbound 配置表';
