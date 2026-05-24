-- ============================================================================
-- 彻底清理 resource_ip_pool_billing 的 bandwidth_mbps + traffic_quota_gb 字段
-- 这俩跟 capacity 子表的 bandwidth_limit_mbps / monthly_traffic_gb 概念重复
-- 现在 账面 = 纯财务 (cost / billing_cycle / expires), 带宽/流量配额全归 capacity
-- ============================================================================

ALTER TABLE resource_ip_pool_billing
  DROP COLUMN bandwidth_mbps,
  DROP COLUMN traffic_quota_gb;

-- ============================================================================
-- 回滚 (慎用; 字段意义已弃用, 即使加回也不再被前后端使用)
-- ============================================================================
-- ALTER TABLE resource_ip_pool_billing
--   ADD COLUMN bandwidth_mbps    INT DEFAULT NULL AFTER ip_id,
--   ADD COLUMN traffic_quota_gb  INT DEFAULT NULL AFTER bandwidth_mbps;
