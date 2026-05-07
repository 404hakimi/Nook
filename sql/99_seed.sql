-- ============================================================
-- 初始数据
-- 直接 SQL 插入需手动提供 created_at / updated_at
-- ============================================================

INSERT INTO `resource_ip_type` (`id`, `code`, `name`, `description`, `sort_order`, `created_at`, `updated_at`) VALUES
(REPLACE(UUID(), '-', ''), 'isp',         'ISP 原生 IP', '机房直签的 ISP 原生住宅标记 IP，AI 平台兼容性最佳', 1, NOW(), NOW()),
(REPLACE(UUID(), '-', ''), 'datacenter',  '机房 IP',     '标准 IDC 机房 IP，价格最低',                          2, NOW(), NOW()),
(REPLACE(UUID(), '-', ''), 'residential', '家宽 IP',     '真实家庭宽带 IP，纯净度最高(第二阶段)',               3, NOW(), NOW());
