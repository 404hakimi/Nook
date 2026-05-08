-- ============================================================
-- 迁移: 把 IP 退订冷却时长从代码硬编码 (DEFAULT_COOLING_MINUTES=30) 移到 resource_ip_type 字段
-- 原因: 不同 IP 类型应有不同冷却策略 (家宽 IP 通常需要更长冷却以避免被识别为同一用户)
-- 适用: 已建库环境; 全新部署的库直接跑 02_resource.sql 即可
-- ============================================================

ALTER TABLE `resource_ip_type`
    ADD COLUMN `cooling_minutes` INT NOT NULL DEFAULT 30 COMMENT 'IP 退订后冷却分钟数 (家宽 IP 一般更长)' AFTER `sort_order`;

-- 给现有三种类型设置合理默认值; 运营可后续在管理后台调整
UPDATE `resource_ip_type` SET `cooling_minutes` = 30  WHERE `code` = 'datacenter';
UPDATE `resource_ip_type` SET `cooling_minutes` = 60  WHERE `code` = 'isp';
UPDATE `resource_ip_type` SET `cooling_minutes` = 120 WHERE `code` = 'residential';
