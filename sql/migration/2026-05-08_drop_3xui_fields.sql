-- ============================================================
-- 迁移: 砍掉 3x-ui 相关字段, all-in Xray gRPC
-- 适用: 已建库的环境
--
-- 决策背景:
--   nook 与 Xray 的对接路径定为方案 C (直接 gRPC), 不再通过 3x-ui 面板.
--   3x-ui 仅作为运营自己装的可视化辅助, 不进 nook 数据/代码.
--   既然单一 backend, backend_type 字段也无意义, 一并删除.
--
-- 影响:
--   resource_server 删 5 列: backend_type, panel_base_url, panel_username,
--                            panel_password, panel_ignore_tls
--   xray_inbound  删 1 列: backend_type
-- ============================================================

ALTER TABLE `resource_server`
    DROP COLUMN `backend_type`,
    DROP COLUMN `panel_base_url`,
    DROP COLUMN `panel_username`,
    DROP COLUMN `panel_password`,
    DROP COLUMN `panel_ignore_tls`;

ALTER TABLE `xray_inbound`
    DROP COLUMN `backend_type`;
