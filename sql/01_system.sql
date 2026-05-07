-- ============================================================
-- 模块: system  后台用户体系
-- ============================================================

-- 后台系统用户(管理员/运营/运维)
CREATE TABLE `system_user` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `username`       VARCHAR(64)     NOT NULL COMMENT '用户名',
    `password_hash`  VARCHAR(255)    NOT NULL COMMENT 'BCrypt 密码哈希',
    `real_name`      VARCHAR(64)     DEFAULT NULL COMMENT '真实姓名',
    `email`          VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    `phone`          VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    `role`           VARCHAR(16)     NOT NULL DEFAULT 'operator' COMMENT '角色: super_admin/operator/devops',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 2=禁用',
    `last_login_at`  DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`  VARCHAR(45)     DEFAULT NULL COMMENT '最后登录IP',
    `remark`         VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台系统用户表';

-- 后台用户操作日志
CREATE TABLE `system_user_log` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `system_user_id` CHAR(32)        NOT NULL COMMENT '关联 system_user.id',
    `action`         VARCHAR(64)     NOT NULL COMMENT '动作: login/generate_cdk/recharge_member/...',
    `target_type`    VARCHAR(32)     DEFAULT NULL COMMENT '操作对象类型: member/cdk/server/ip',
    `target_id`      CHAR(32)        DEFAULT NULL COMMENT '操作对象ID',
    `detail`         JSON            DEFAULT NULL COMMENT '操作详情',
    `client_ip`      VARCHAR(45)     DEFAULT NULL COMMENT '客户端IP',
    `client_ua`      VARCHAR(512)    DEFAULT NULL COMMENT '客户端UA',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户操作日志表';
