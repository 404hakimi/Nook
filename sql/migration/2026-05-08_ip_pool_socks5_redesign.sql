-- ============================================================
-- 迁移: resource_ip_pool 重设计为 SOCKS5 落地节点池
-- 适用: 已建库且 resource_ip_pool 旧表(server_id/port 模型)的环境
-- 全新部署的库无需执行(新版 sql/02_resource.sql 已是最终结构)
--
-- 变更:
--   旧表把 IP 视为"绑定在 server 上的端口" — 不符合 SOCKS5 落地节点架构
--   新表把 IP 视为独立的 SOCKS5 落地节点(自己买的小 VPS 跑 3proxy)
--   - 删 server_id  (落地节点不再绑定到某条线路, 任意线路都能 outbound 到这个 SOCKS5)
--   - 删 port       (用 socks5_port 替代)
--   - 加 region     (用户买的"地区", 由这台 VPS 物理位置决定)
--   - 加 socks5_*   (host/port/user/password)
--   - 加 cooling_until (冷却时间戳)
--
-- ⚠️ 旧数据语义已变,机械迁移没有意义.如果你之前 resource_ip_pool 里录过测试数据,
--   先备份再 drop+create.
-- ============================================================

DROP TABLE IF EXISTS `resource_ip_pool`;

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
    `cooling_until`      DATETIME       DEFAULT NULL COMMENT '冷却到期时间',
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
