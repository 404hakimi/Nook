-- ============================================================
-- 迁移: resource_server 加月流量额度字段
-- 适用: 已经按旧 DDL 部署过、需要原地变更的环境
-- 全新部署的库无需执行(新版 sql/02_resource.sql 已含此列)
--
-- 设计:
--   total_bandwidth 是带宽峰值 Mbps(端口能跑多快)
--   monthly_traffic_gb 是月流量额度 GB(VPS 套餐限额)；null/0 表示不限或未配置
--   两者独立，因为同一台机器可能"100Mbps/不限流量"或"1Gbps/5TB"等任意组合
-- ============================================================

ALTER TABLE `resource_server`
    ADD COLUMN `monthly_traffic_gb` INT DEFAULT NULL
        COMMENT '月流量额度 GB(VPS 套餐限额，0/null=不限或未配置)' AFTER `total_bandwidth`;
