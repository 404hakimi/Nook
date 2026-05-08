-- ============================================================
-- 模块: resource  服务器/IP 资源
-- ============================================================

-- 服务器(出口VPS)
-- backend_type 决定 nook 怎么对接这台机器：
--   threexui  → 通过 3x-ui 面板 HTTP API；只需要面板凭据
--   xray-grpc → 通过 Xray 内核的 gRPC API；需要 gRPC 端口/凭据；面板字段留空即可
-- ssh 字段双 backend 都用得上(运维命令、本地端口转发)，独立成 ssh_user/ssh_password
CREATE TABLE `resource_server` (
    `id`               CHAR(32)        NOT NULL COMMENT '主键ID',
    `name`             VARCHAR(64)     NOT NULL COMMENT '别名: us-west-rn-01',
    `host`             VARCHAR(128)    NOT NULL COMMENT '管理 IP/域名',
    `ssh_port`         INT             NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
    `ssh_user`         VARCHAR(64)     NOT NULL DEFAULT 'root' COMMENT 'SSH 用户',
    `ssh_password`     VARCHAR(255)    DEFAULT NULL COMMENT 'SSH 密码(明文,TODO 加密)；与 ssh_private_key 二选一',
    `ssh_private_key`  TEXT            DEFAULT NULL COMMENT 'SSH 私钥 PEM 文本(明文,TODO 加密)',
    `ssh_timeout_seconds` INT          NOT NULL DEFAULT 30 COMMENT 'SSH 命令最大耗时(秒)；跨洲网络/拉日志慢可调高，建议 30-120',
    `backend_type`     VARCHAR(16)     NOT NULL DEFAULT 'threexui' COMMENT '后端类型: threexui / xray-grpc',
    `panel_base_url`   VARCHAR(255)    DEFAULT NULL COMMENT '3x-ui 面板入口含 webBasePath, 例 https://x.com:2053/abc',
    `panel_username`   VARCHAR(64)     DEFAULT NULL COMMENT '3x-ui 面板登录名',
    `panel_password`   VARCHAR(255)    DEFAULT NULL COMMENT '3x-ui 面板密码(明文,TODO 加密)',
    `panel_ignore_tls` TINYINT         NOT NULL DEFAULT 0 COMMENT '是否跳过面板 HTTPS 证书校验: 0=否 1=是(自签证书时)',
    `backend_timeout_seconds` INT      NOT NULL DEFAULT 20 COMMENT 'backend HTTP/gRPC 调用超时(秒)；跨洲建议 20-60',
    `xray_grpc_host`   VARCHAR(128)    DEFAULT NULL COMMENT 'Xray gRPC API 主机；通常 127.0.0.1，需借助 SSH 隧道访问',
    `xray_grpc_port`   INT             DEFAULT NULL COMMENT 'Xray gRPC API 端口(内网/本地)',
    `total_bandwidth`  INT             NOT NULL DEFAULT 1000 COMMENT '带宽峰值速率 Mbps(端口能跑多快)',
    `monthly_traffic_gb` INT           DEFAULT NULL COMMENT '月流量额度 GB(VPS 套餐限额，0/null=不限或未配置)',
    `total_ip_count`   INT             NOT NULL DEFAULT 0 COMMENT '该服务器拥有的 IP 总数',
    `idc_provider`     VARCHAR(64)     DEFAULT NULL COMMENT 'IDC 供应商: racknerd/hosthatch/dmit',
    `region`           VARCHAR(64)     DEFAULT NULL COMMENT '区域: us-west/us-east/jp/hk',
    `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=运行 2=维护 3=下线',
    `remark`           VARCHAR(512)    DEFAULT NULL COMMENT '备注',
    `created_at`       DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`       DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器表';

-- IP 类型(售卖维度: ISP / 机房 / 家宽)
CREATE TABLE `resource_ip_type` (
    `id`          CHAR(32)        NOT NULL COMMENT '主键ID',
    `code`        VARCHAR(32)     NOT NULL COMMENT '类型编码: isp/datacenter/residential',
    `name`        VARCHAR(64)     NOT NULL COMMENT '展示名称',
    `description` VARCHAR(255)    DEFAULT NULL COMMENT '描述',
    `sort_order`  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at`  DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 类型表';

-- IP 池
CREATE TABLE `resource_ip_pool` (
    `id`                 CHAR(32)       NOT NULL COMMENT '主键ID',
    `server_id`          CHAR(32)       NOT NULL COMMENT '关联 resource_server.id',
    `ip_type_id`         CHAR(32)       NOT NULL COMMENT '关联 resource_ip_type.id',
    `ip_address`         VARCHAR(45)    NOT NULL COMMENT 'IP 地址',
    `port`               INT            NOT NULL COMMENT 'Xray inbound 监听端口',
    `status`             TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded',
    `assigned_member_id` CHAR(32)       DEFAULT NULL COMMENT '当前分配的会员ID',
    `assigned_at`        DATETIME       DEFAULT NULL COMMENT '分配时间',
    `score`              DECIMAL(5,2)   NOT NULL DEFAULT 100.00 COMMENT 'IP 综合评分 0-100',
    `scamalytics_score`  INT            DEFAULT NULL COMMENT 'Scamalytics 欺诈评分',
    `ipqs_score`         INT            DEFAULT NULL COMMENT 'IPQualityScore 风险评分',
    `assign_count`       INT            NOT NULL DEFAULT 0 COMMENT '历史被分配次数',
    `last_health_at`     DATETIME       DEFAULT NULL COMMENT '最后健康检查时间',
    `remark`             VARCHAR(255)   DEFAULT NULL COMMENT '备注',
    `created_at`         DATETIME       NOT NULL COMMENT '创建时间',
    `updated_at`         DATETIME       NOT NULL COMMENT '更新时间',
    `deleted`            TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 池表';

-- IP 平台兼容性(每平台一行，平台可扩展)
CREATE TABLE `resource_ip_compat` (
    `id`         CHAR(32)        NOT NULL COMMENT '主键ID',
    `ip_id`      CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `platform`   VARCHAR(32)     NOT NULL COMMENT '平台: chatgpt/claude/gemini/midjourney/...',
    `status`     TINYINT         NOT NULL COMMENT '状态: 1=可用 2=被拦截 3=未知',
    `detail`     VARCHAR(255)    DEFAULT NULL COMMENT '检测详情',
    `checked_at` DATETIME        NOT NULL COMMENT '检测时间',
    `created_at` DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME        NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 平台兼容性表';
