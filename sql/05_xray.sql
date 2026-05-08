-- ============================================================
-- 模块: xray  Xray 内核对接 (双 backend: 3x-ui / Xray gRPC)
-- ============================================================
--
-- 模型说明：
--   ① "inbound" 在两种 backend 里语义略有差异：3x-ui 把 inbound 视为一个监听端口+协议
--      绑定一组 client；Xray gRPC 同样以 inbound_tag 为单位挂载 user。本表统一以
--      "一个会员在某 IP 上的接入凭据" 为粒度，落地行 = 一个客户端 (vless user / vmess user / trojan password)。
--   ② inbound 实例(端口/协议/transport/streamSettings)由 IP 维度预先建好，本表只管"client → IP/inbound"映射。
--   ③ external_inbound_ref 跨 backend 通用：
--        threexui  → 面板 inbound id (整数字符串)
--        xray-grpc → inbound tag (字符串)
--   ④ client_email 在 3x-ui 是 panel 的 email 字段；gRPC 模式我们用 "member_{memberUserId}_{ipId}" 当 user.email
--      复用。两边都是人类可读且唯一的标识。
--   ⑤ client_uuid 是协议级密钥：vless/vmess 的 user UUID，trojan 的 password。
--
-- 观测指标(累计上下行)走 monitor_traffic_snapshot，本表只管配置/状态。

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
