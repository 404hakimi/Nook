-- ============================================================
-- 迁移: resource_server 双 backend 凭据 + 超时配置
-- 适用: 已经按旧 DDL 部署过、需要原地变更的环境
-- 全新部署的库无需执行(新版 sql/02_resource.sql 已含全部字段)
--
-- 变更内容:
--   1. 新增 SSH 用户/密码/私钥/超时字段(原 DDL 只有 ssh_port)
--   2. 新增 backend_type + 3x-ui 面板字段 + Xray gRPC 主机字段
--   3. 新增 backend_timeout_seconds(HTTP/gRPC 调用超时)
--   4. xray_grpc_port 从 NOT NULL DEFAULT 10085 改为 nullable(gRPC 模式才填)
--   5. 删除已废弃的 grpc_auth_token(凭据合并进 backend 配置)
--
-- 生产前请在测试库验证一次再 apply 到 prod
-- ============================================================

ALTER TABLE `resource_server`
    ADD COLUMN `ssh_user`            VARCHAR(64)  NOT NULL DEFAULT 'root'
        COMMENT 'SSH 用户' AFTER `ssh_port`,
    ADD COLUMN `ssh_password`        VARCHAR(255) DEFAULT NULL
        COMMENT 'SSH 密码(明文,TODO 加密)；与 ssh_private_key 二选一' AFTER `ssh_user`,
    ADD COLUMN `ssh_private_key`     TEXT         DEFAULT NULL
        COMMENT 'SSH 私钥 PEM 文本(明文,TODO 加密)' AFTER `ssh_password`,
    ADD COLUMN `ssh_timeout_seconds` INT          NOT NULL DEFAULT 30
        COMMENT 'SSH 命令最大耗时(秒)；跨洲网络/拉日志慢可调高，建议 30-120' AFTER `ssh_private_key`,
    ADD COLUMN `backend_type`        VARCHAR(16)  NOT NULL DEFAULT 'threexui'
        COMMENT '后端类型: threexui / xray-grpc' AFTER `ssh_timeout_seconds`,
    ADD COLUMN `panel_base_url`      VARCHAR(255) DEFAULT NULL
        COMMENT '3x-ui 面板入口含 webBasePath, 例 https://x.com:2053/abc' AFTER `backend_type`,
    ADD COLUMN `panel_username`      VARCHAR(64)  DEFAULT NULL
        COMMENT '3x-ui 面板登录名' AFTER `panel_base_url`,
    ADD COLUMN `panel_password`      VARCHAR(255) DEFAULT NULL
        COMMENT '3x-ui 面板密码(明文,TODO 加密)' AFTER `panel_username`,
    ADD COLUMN `panel_ignore_tls`    TINYINT      NOT NULL DEFAULT 0
        COMMENT '是否跳过面板 HTTPS 证书校验: 0=否 1=是(自签证书时)' AFTER `panel_password`,
    ADD COLUMN `backend_timeout_seconds` INT      NOT NULL DEFAULT 20
        COMMENT 'backend HTTP/gRPC 调用超时(秒)；跨洲建议 20-60' AFTER `panel_ignore_tls`,
    ADD COLUMN `xray_grpc_host`      VARCHAR(128) DEFAULT NULL
        COMMENT 'Xray gRPC API 主机；通常 127.0.0.1，需借助 SSH 隧道访问' AFTER `backend_timeout_seconds`;

-- xray_grpc_port: 旧版 NOT NULL DEFAULT 10085 → 改为 nullable(gRPC 模式才填)
ALTER TABLE `resource_server`
    MODIFY COLUMN `xray_grpc_port` INT DEFAULT NULL
        COMMENT 'Xray gRPC API 端口(内网/本地)';

-- 删除已废弃字段(凭据合并进 backend 配置体系，不再单独存)
-- 如果你的环境中此列已不存在，IGNORE 这一步即可
ALTER TABLE `resource_server` DROP COLUMN `grpc_auth_token`;
