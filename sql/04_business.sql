-- ============================================================
-- 模块: business  套餐 / CDK / 直充 / 订单流水
-- ============================================================

-- 套餐
CREATE TABLE `business_plan` (
    `id`              CHAR(32)       NOT NULL COMMENT '主键ID',
    `name`            VARCHAR(64)    NOT NULL COMMENT '套餐名称',
    `type`            VARCHAR(16)    NOT NULL COMMENT '套餐类型: trial/month/quarter/half_year/year',
    `ip_type_id`      CHAR(32)       NOT NULL COMMENT '关联 resource_ip_type.id',
    `duration_days`   INT            NOT NULL COMMENT '时长(天)',
    `price`           DECIMAL(10,2)  NOT NULL COMMENT '价格(元)',
    `ip_count`        INT            NOT NULL DEFAULT 1 COMMENT '包含独享 IP 数量',
    `max_devices`     INT            NOT NULL DEFAULT 3 COMMENT '允许同时在线设备数',
    `bandwidth_limit` INT            NOT NULL DEFAULT 30 COMMENT '带宽限速 Mbps',
    `traffic_limit`   BIGINT         DEFAULT NULL COMMENT '月流量上限(字节)，NULL=不限',
    `is_active`       TINYINT        NOT NULL DEFAULT 1 COMMENT '是否上架: 1=上架 0=下架',
    `sort_order`      INT            NOT NULL DEFAULT 0 COMMENT '排序',
    `description`     VARCHAR(512)   DEFAULT NULL COMMENT '描述',
    `created_at`      DATETIME       NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME       NOT NULL COMMENT '更新时间',
    `deleted`         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐表';

-- CDK 卡密
CREATE TABLE `business_cdk` (
    `id`                  CHAR(32)        NOT NULL COMMENT '主键ID',
    `code_hash`           VARCHAR(128)    NOT NULL COMMENT 'SHA-256(原始CDK)',
    `code_prefix`         VARCHAR(4)      NOT NULL COMMENT 'CDK 前4位明文(模糊查询)',
    `plan_id`             CHAR(32)        NOT NULL COMMENT '关联 business_plan.id',
    `batch_no`            VARCHAR(32)     DEFAULT NULL COMMENT '生成批次号',
    `status`              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=unused 2=used 3=disabled 4=expired',
    `expire_at`           DATETIME        DEFAULT NULL COMMENT 'CDK 自身过期时间',
    `generated_by`        CHAR(32)        DEFAULT NULL COMMENT '生成人 system_user.id',
    `redeemed_by`         CHAR(32)        DEFAULT NULL COMMENT '兑换人 member_user.id',
    `redeemed_at`         DATETIME        DEFAULT NULL COMMENT '兑换时间',
    `redeemed_client_ip`  VARCHAR(45)     DEFAULT NULL COMMENT '兑换时客户端IP',
    `redeemed_client_ua`  VARCHAR(512)    DEFAULT NULL COMMENT '兑换时客户端UA',
    `redeemed_device_fp`  VARCHAR(128)    DEFAULT NULL COMMENT '兑换时设备指纹',
    `remark`              VARCHAR(512)    DEFAULT NULL COMMENT '备注: 闲鱼订单号/买家昵称等',
    `created_at`          DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDK 卡密表';

-- 后台直充记录(管理员手动给指定会员添加套餐)
CREATE TABLE `business_recharge` (
    `id`             CHAR(32)        NOT NULL COMMENT '主键ID',
    `system_user_id` CHAR(32)        NOT NULL COMMENT '操作的后台用户 system_user.id',
    `member_user_id` CHAR(32)        NOT NULL COMMENT '目标会员 member_user.id',
    `plan_id`        CHAR(32)        NOT NULL COMMENT '关联 business_plan.id',
    `duration_days`  INT             NOT NULL COMMENT '充值天数',
    `reason`         VARCHAR(255)    NOT NULL COMMENT '充值原因: 售后补偿/内测赠送/合作置换',
    `created_at`     DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台直充记录表';

-- 订单流水(所有导致会员订阅变更的事件)
CREATE TABLE `business_order_log` (
    `id`              CHAR(32)        NOT NULL COMMENT '主键ID',
    `member_user_id`  CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `subscription_id` CHAR(32)        DEFAULT NULL COMMENT '关联 member_subscription.id',
    `type`            VARCHAR(32)     NOT NULL COMMENT '事件类型: cdk_redeem/admin_recharge/change_ip/refund/renew',
    `ref_id`          CHAR(32)        DEFAULT NULL COMMENT '关联 business_cdk.id / business_recharge.id 等',
    `detail`          JSON            DEFAULT NULL COMMENT '变更详情',
    `created_at`      DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单流水表';
