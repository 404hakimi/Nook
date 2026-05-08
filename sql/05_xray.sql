-- ============================================================
-- 模块: xray  Xray 内核对接
-- ============================================================
--
-- 模型说明:
--   ① 每个会员在某条线路 + 某个 SOCKS5 出口上的一组接入凭据 = 一行 (= Xray "client")
--   ② nook 通过 Xray gRPC 在中转线路服务器的 inbound 里挂这个 client
--      同时为 client 加 socks5 outbound + email-based routing rule, 让流量从指定 IP 出
--   ③ external_inbound_ref = 远端 Xray inbound 的 tag (与 xray.json 里的 tag 一致),
--      指明该 client 挂到哪个 inbound 下。inbound 自身 (vmess / vless 监听端口等) 由部署脚本
--      写死在远端 xray.json, nook 不动。
--   ④ client_email 唯一, 既是 routing rule 的匹配键也是流量统计的索引;
--      格式建议 member_{memberId}_{ipId}, server 内全局唯一
--   ⑤ client_uuid 是协议级密钥 (vless/vmess 是 UUID; trojan 是 password)
--
-- 观测指标 (累计上下行) 走 monitor_traffic_snapshot, 本表只管配置 / 状态。

CREATE TABLE `xray_client` (
    `id`                   CHAR(32)        NOT NULL COMMENT '主键ID',
    `server_id`            CHAR(32)        NOT NULL COMMENT '关联 resource_server.id (中转线路)',
    `ip_id`                CHAR(32)        NOT NULL COMMENT '关联 resource_ip_pool.id (落地 SOCKS5)',
    `member_user_id`       CHAR(32)        NOT NULL COMMENT '关联 member_user.id',
    `external_inbound_ref` VARCHAR(128)    NOT NULL COMMENT '挂到的远端 Xray inbound tag',
    `protocol`             VARCHAR(16)     NOT NULL DEFAULT 'vmess' COMMENT '协议: vmess / vless / trojan / shadowsocks',
    `transport`            VARCHAR(32)     DEFAULT NULL COMMENT '传输: tcp / ws / grpc / xhttp 等',
    `listen_ip`            VARCHAR(45)     DEFAULT NULL COMMENT 'inbound 监听IP',
    `listen_port`          INT             NOT NULL COMMENT '客户端实际连接端口',
    `client_uuid`          VARCHAR(64)     NOT NULL COMMENT '协议级密钥: vless/vmess UUID, trojan password',
    `client_email`         VARCHAR(128)    NOT NULL COMMENT '人类可读标识; 建议 member_{memberId}_{ipId}',
    `status`               TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=运行 2=已停 3=待同步 4=远端已不存在',
    `last_synced_at`       DATETIME        DEFAULT NULL COMMENT '最近一次与 Xray 对账成功时间',
    `created_at`           DATETIME        NOT NULL COMMENT '创建时间',
    `updated_at`           DATETIME        NOT NULL COMMENT '更新时间',
    `deleted`              TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删 1=已删',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Xray 客户端表 (会员 + 线路 + 落地 IP 三元组凭据)';
