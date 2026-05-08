-- ============================================================
-- 模块: resource  服务器/IP 资源
-- ============================================================

-- 服务器(中转线路, 跑 Xray 内核, 由 nook 通过 SSH 隧道 + gRPC 远程调度)
CREATE TABLE `resource_server` (
    `id`               CHAR(32)        NOT NULL COMMENT '主键ID',
    `name`             VARCHAR(64)     NOT NULL COMMENT '别名: us-west-rn-01',
    `host`             VARCHAR(128)    NOT NULL COMMENT '管理 IP/域名',
    `ssh_port`         INT             NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
    `ssh_user`         VARCHAR(64)     NOT NULL DEFAULT 'root' COMMENT 'SSH 用户',
    `ssh_password`     VARCHAR(255)    DEFAULT NULL COMMENT 'SSH 密码(明文,TODO 加密)；与 ssh_private_key 二选一',
    `ssh_private_key`  TEXT            DEFAULT NULL COMMENT 'SSH 私钥 PEM 文本(明文,TODO 加密)',
    `ssh_timeout_seconds` INT          NOT NULL DEFAULT 30 COMMENT 'SSH 命令最大耗时(秒)；跨洲网络/拉日志慢可调高，建议 30-120',
    `backend_timeout_seconds` INT      NOT NULL DEFAULT 20 COMMENT 'backend gRPC 调用超时(秒)；跨洲建议 20-60',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中转线路服务器';

-- IP 类型(售卖维度: ISP / 机房 / 家宽)
CREATE TABLE `resource_ip_type` (
    `id`          CHAR(32)        NOT NULL COMMENT '主键ID',
    `code`        VARCHAR(32)     NOT NULL COMMENT '类型编码: isp/datacenter/residential',
    `name`        VARCHAR(64)     NOT NULL COMMENT '展示名称',
    `description` VARCHAR(255)    DEFAULT NULL COMMENT '描述',
    `sort_order`  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    `cooling_minutes` INT         NOT NULL DEFAULT 30 COMMENT 'IP 退订后冷却分钟数 (家宽 IP 一般更长)',
    `created_at`  DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 类型表';

-- IP 池(实际是 SOCKS5 落地节点池)
-- 每条记录 = 一台跑了 3proxy 的小 VPS, 它的公网 IP 就是用户最终对外暴露的"独享 IP"
-- 业务流程:
--   nook 在中转线路服务器(resource_server)上,通过 Xray gRPC 给一个会员 client 加 outbound:
--     {protocol: socks, settings: {servers: [{address: socks5_host, port: socks5_port,
--      users: [{user: socks5_username, pass: socks5_password}]}]}}
--   再加 routing rule: client.email = member_X → outbound tag = user_X_out
--   该会员的流量就从这个 SOCKS5 出网, 公网看到的是 ip_address
--
-- ip_address vs socks5_host:
--   通常二者相等(你买的 VPS 自己装 3proxy, 公网 IP 既是 SOCKS5 入口也是出网 IP)
--   独立成两个字段是为了将来支持"SOCKS5 服务在 A 主机, 出网走 B 主机(NAT/隧道)"的扩展场景
CREATE TABLE `resource_ip_pool` (
    `id`                 CHAR(32)       NOT NULL COMMENT '主键ID',
    `region`             VARCHAR(64)    NOT NULL COMMENT '区域: us-west / us-east / jp / hk / sg / ...',
    `ip_type_id`         CHAR(32)       NOT NULL COMMENT '关联 resource_ip_type.id (ISP/家宽/机房)',
    `ip_address`         VARCHAR(45)    NOT NULL COMMENT '出网真实 IP, 用户最终对外暴露的就是这个',
    `socks5_host`        VARCHAR(128)   NOT NULL COMMENT 'SOCKS5 server 地址(通常 = ip_address)',
    `socks5_port`        INT            NOT NULL COMMENT 'SOCKS5 端口',
    `socks5_username`    VARCHAR(64)    DEFAULT NULL COMMENT 'SOCKS5 用户名(明文,TODO 加密)',
    `socks5_password`    VARCHAR(255)   DEFAULT NULL COMMENT 'SOCKS5 密码(明文,TODO 加密)',
    `status`             TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded',
    `assigned_member_id` CHAR(32)       DEFAULT NULL COMMENT '当前分配的会员ID',
    `assigned_at`        DATETIME       DEFAULT NULL COMMENT '分配时间',
    `cooling_until`      DATETIME       DEFAULT NULL COMMENT '冷却到期时间(退订后不立刻给下个用户避免历史影响)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 池(SOCKS5 落地节点)';

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
