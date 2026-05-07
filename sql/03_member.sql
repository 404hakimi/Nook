-- ============================================================
-- 模块: member  会员
-- ============================================================

-- 会员用户
CREATE TABLE `member_user` (
    `id`              CHAR(32)        NOT NULL COMMENT '主键ID',
    `username`        VARCHAR(64)     NOT NULL COMMENT '用户名',
    `password_hash`   VARCHAR(255)    NOT NULL COMMENT 'BCrypt 密码哈希',
    `email`           VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    `phone`           VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 2=封禁 3=注销',
    `register_ip`     VARCHAR(45)     DEFAULT NULL COMMENT '注册IP',
    `register_ua`     VARCHAR(512)    DEFAULT NULL COMMENT '注册UA',
    `last_login_at`   DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(45)     DEFAULT NULL COMMENT '最后登录IP',
    `sub_token`       VARCHAR(64)     NOT NULL COMMENT '订阅链接令牌(可重置)',
    `max_devices`     INT             NOT NULL DEFAULT 3 COMMENT '设备数上限(套餐覆盖)',
    `remark`          VARCHAR(512)    DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员用户表';

-- 会员设备(用于设备数限制 / 在线管理)
CREATE TABLE `member_device` (
    `id`                 CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id`     CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `device_fingerprint` VARCHAR(128)    NOT NULL COMMENT '设备指纹',
    `device_name`        VARCHAR(128)    DEFAULT NULL COMMENT '设备名称',
    `client_type`        VARCHAR(32)     DEFAULT NULL COMMENT '客户端类型: clash/v2rayn/singbox/unknown',
    `client_ip`          VARCHAR(45)     DEFAULT NULL COMMENT '最近一次连接IP',
    `last_active_at`     DATETIME        NOT NULL COMMENT '最后活跃时间',
    `status`             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=在线 2=离线',
    `created_at`         DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`         DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员设备表';

-- 会员操作日志(登录/兑换/换IP/重置订阅 等)
CREATE TABLE `member_log` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id` CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `action`         VARCHAR(64)     NOT NULL COMMENT '动作: login/redeem/change_ip/reset_sub',
    `detail`         JSON            DEFAULT NULL COMMENT '操作详情',
    `client_ip`      VARCHAR(45)     DEFAULT NULL COMMENT '客户端IP',
    `client_ua`      VARCHAR(512)    DEFAULT NULL COMMENT '客户端UA',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员操作日志表';

-- 会员订阅(当前生效套餐)
CREATE TABLE `member_subscription` (
    `id`              CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id`  CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `plan_id`         CHAR(32)        NOT NULL COMMENT '关联 business_plan.id',
    `ip_type_id`      CHAR(32)        NOT NULL COMMENT '关联 resource_ip_type.id 冗余便于筛选',
    `max_devices`     INT             NOT NULL DEFAULT 3 COMMENT '设备数上限',
    `bandwidth_limit` INT             NOT NULL DEFAULT 30 COMMENT '带宽限速 Mbps',
    `traffic_limit`   BIGINT          DEFAULT NULL COMMENT '月流量上限(字节)，NULL=不限',
    `start_at`        DATETIME        NOT NULL COMMENT '开始时间',
    `expire_at`       DATETIME        NOT NULL COMMENT '到期时间',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=生效中 2=已过期 3=已暂停 4=已退款',
    `source`          VARCHAR(16)     NOT NULL COMMENT '来源: cdk/admin_recharge',
    `source_id`       CHAR(32)        DEFAULT NULL COMMENT '关联 business_cdk.id 或 business_recharge.id',
    `created_at`      DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订阅表';
