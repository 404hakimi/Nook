-- ============================================================
-- 初始数据
-- 直接 SQL 插入需手动提供 created_at / updated_at
-- ============================================================

-- IP 类型(售卖维度); 不同类型设不同冷却时长 (家宽 IP 因被识别风险更高, 冷却更久)
INSERT INTO `resource_ip_type` (`id`, `code`, `name`, `description`, `sort_order`, `cooling_minutes`, `created_at`, `updated_at`) VALUES
(REPLACE(UUID(), '-', ''), 'isp',         'ISP 原生 IP', '机房直签的 ISP 原生住宅标记 IP，AI 平台兼容性最佳', 1,  60, NOW(), NOW()),
(REPLACE(UUID(), '-', ''), 'datacenter',  '机房 IP',     '标准 IDC 机房 IP，价格最低',                          2,  30, NOW(), NOW()),
(REPLACE(UUID(), '-', ''), 'residential', '家宽 IP',     '真实家庭宽带 IP，纯净度最高(第二阶段)',               3, 120, NOW(), NOW());

-- 默认超级管理员
-- 用户名: admin
-- 密  码: admin123 (BCrypt cost=10 哈希；首次登录后请立即在管理后台修改)
INSERT INTO `system_user` (`id`, `username`, `password_hash`, `real_name`, `role`, `status`, `remark`, `created_at`, `updated_at`) VALUES
(REPLACE(UUID(), '-', ''), 'admin',
 '$2a$10$AxQefGxwfY0TTmLUj2lWnOHeH95JeAJnWmCNIey/OuqedRY44baMm',
 '超级管理员', 'super_admin', 1, '初始账号，请尽快修改密码', NOW(), NOW());
