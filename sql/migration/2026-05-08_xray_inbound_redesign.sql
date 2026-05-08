-- ============================================================
-- 迁移: xray_inbound 表重设计为"client 映射表" (支持双 backend)
-- 适用: 已经按旧 DDL 部署过、需要原地变更的环境
-- 全新部署的库无需执行(新版 sql/05_xray.sql 已是最终结构)
--
-- 变更内容(语义):
--   旧表是 "Xray 原生 inbound 配置" 一行=一个 inbound (端口/transport/streamSettings)
--   新表是 "client 映射"           一行=一个会员在某 IP 上的 client (vless/vmess user 或 trojan password)
--   inbound 实例(端口/协议)由 IP 维度预先建好，本表只管 client → IP/inbound 映射。
--
-- 字段层面:
--   inbound_tag → external_inbound_ref  (跨 backend 通用引用键: 3xui=面板 inboundId, grpc=inbound tag)
--   uuid        → client_uuid           (协议级密钥: vless/vmess UUID, trojan password)
--   新增 client_email                    (人类可读标识 / 3x-ui 的 client.email)
--   新增 backend_type                    (与 server 一致)
--   新增 last_synced_at                  (reconciler 对账时间)
--   新增 deleted                         (逻辑删除)
--   transport / listen_ip 改为可空(3xui 模式下从面板读，DB 不强存)
--
-- ⚠️ 重要: 旧表数据语义变了，没法机械迁移(旧 inbound_tag 不等于新 external_inbound_ref)。
--   有两种处理方式，根据你环境数据情况二选一:
--
--   方案 A (推荐, 当前没有真实数据时): 直接 DROP + CREATE
--   方案 B (有少量旧数据需保留): 跑下面的 ALTER 后人工补 client_email/backend_type
--
-- 默认提供方案 A; 方案 B 在文件末尾注释里给参考。
-- ============================================================

-- 方案 A: 重建表
DROP TABLE IF EXISTS `xray_inbound`;

CREATE TABLE `xray_inbound` (
    `id`                   CHAR(32)        NOT NULL COMMENT '主键ID',
    `server_id`            CHAR(32)        NOT NULL COMMENT '关联 resource_server.id',
    `ip_id`                CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id',
    `member_user_id`       CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `backend_type`         VARCHAR(16)     NOT NULL COMMENT '后端类型: threexui / xray-grpc; 与所属 server 的 backend_type 一致',
    `external_inbound_ref` VARCHAR(128)    NOT NULL COMMENT '后端引用键: threexui=面板 inboundId; xray-grpc=inbound tag',
    `protocol`             VARCHAR(16)     NOT NULL DEFAULT 'vless' COMMENT '协议: vless / vmess / trojan / shadowsocks',
    `transport`            VARCHAR(32)     DEFAULT NULL COMMENT '传输: reality / ws / tcp / grpc / xhttp 等; 主要给 gRPC backend 用',
    `listen_ip`            VARCHAR(45)     DEFAULT NULL COMMENT 'inbound 监听IP; 单 IP VPS 通常 0.0.0.0 或 127.0.0.1',
    `listen_port`          INT             NOT NULL COMMENT '客户端实际连接端口',
    `client_uuid`          VARCHAR(64)     NOT NULL COMMENT '协议级密钥: vless/vmess 的 UUID, trojan 的 password',
    `client_email`         VARCHAR(128)    NOT NULL COMMENT '人类可读标识 / 3x-ui 的 client.email; 建议格式 member_{memberId}_{ipId}',
    `status`               TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=运行 2=已停 3=待同步 4=远端已不存在(reconciler 标记)',
    `last_synced_at`       DATETIME        DEFAULT NULL COMMENT '最近一次与远端 backend 对账成功时间',
    `created_at`           DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`           DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`              TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Xray/3x-ui 客户端配置表';

-- ============================================================
-- 方案 B (参考): 不丢数据的原地变更
-- 如果你有少量旧 xray_inbound 行需要保留, 把上面的 DROP+CREATE 注释掉，跑下面这段:
-- ============================================================
-- ALTER TABLE `xray_inbound`
--     CHANGE COLUMN `inbound_tag` `external_inbound_ref` VARCHAR(128) NOT NULL
--         COMMENT '后端引用键',
--     CHANGE COLUMN `uuid` `client_uuid` VARCHAR(64) NOT NULL
--         COMMENT '协议级密钥',
--     MODIFY COLUMN `transport` VARCHAR(32) DEFAULT NULL
--         COMMENT '传输方式',
--     MODIFY COLUMN `listen_ip` VARCHAR(45) DEFAULT NULL
--         COMMENT '监听IP',
--     ADD COLUMN `backend_type` VARCHAR(16) NOT NULL DEFAULT 'threexui'
--         COMMENT '后端类型' AFTER `member_user_id`,
--     ADD COLUMN `client_email` VARCHAR(128) NOT NULL DEFAULT ''
--         COMMENT 'client email; 旧数据需手工补' AFTER `client_uuid`,
--     ADD COLUMN `last_synced_at` DATETIME DEFAULT NULL
--         COMMENT '最近对账时间' AFTER `status`,
--     ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0
--         COMMENT '逻辑删除';
-- -- 旧数据补 client_email; member_user_id+ip_id 拼出来与 service.provision 默认格式对齐
-- UPDATE `xray_inbound`
--    SET `client_email` = CONCAT('member_', `member_user_id`, '_', `ip_id`)
--  WHERE `client_email` = '';
