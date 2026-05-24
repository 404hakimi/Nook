-- ============================================================================
-- 新增 resource_ip_pool_capacity 子表 (跟 resource_server_capacity 一致)
-- 并把 bandwidth_limit_mbps 从 socks5 子表迁过来
-- 跑序: 先建表 + 回填, 再删 socks5 子表的列
-- ============================================================================

-- 1. 建容量子表 (1:1 with resource_ip_pool)
CREATE TABLE IF NOT EXISTS resource_ip_pool_capacity (
  ip_id                CHAR(32)     PRIMARY KEY                       COMMENT 'FK -> resource_ip_pool.id',
  bandwidth_limit_mbps INT          NOT NULL DEFAULT 0                COMMENT 'dante 实际限速 Mbps; 0=不限; agent 改 sockd.conf 落实',
  monthly_traffic_gb   INT                                            COMMENT '月流量上限 GB; null/0=不限. throttle 90% 触发基数',
  used_traffic_bytes   BIGINT       NOT NULL DEFAULT 0                COMMENT '当周期累计已用 = rx + tx (Agent push)',
  rx_bytes             BIGINT       NOT NULL DEFAULT 0                COMMENT '当周期下行字节 (vnstat rx)',
  tx_bytes             BIGINT       NOT NULL DEFAULT 0                COMMENT '当周期上行字节 (vnstat tx)',
  quota_reset_policy   VARCHAR(32)  NOT NULL DEFAULT 'CALENDAR_MONTH' COMMENT 'CALENDAR_MONTH / BILLING_CYCLE / FIXED',
  throttle_state       VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'         COMMENT 'NORMAL / THROTTLED; used 90% 翻 THROTTLED (后续做)',
  created_at           DATETIME     NOT NULL,
  updated_at           DATETIME     NOT NULL,
  CONSTRAINT fk_ip_pool_capacity_ip FOREIGN KEY (ip_id) REFERENCES resource_ip_pool(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 池容量监控 (1:1 跟 resource_ip_pool); agent 上报流量后写入';

-- 2. 回填: 对已有 IP 池行插容量空记录, bandwidth_limit_mbps 从 socks5 子表搬过来
INSERT INTO resource_ip_pool_capacity (ip_id, bandwidth_limit_mbps, created_at, updated_at)
SELECT s.ip_id, COALESCE(s.bandwidth_limit_mbps, 0), NOW(), NOW()
FROM resource_ip_pool_socks5 s
LEFT JOIN resource_ip_pool_capacity c ON c.ip_id = s.ip_id
WHERE c.ip_id IS NULL;

-- 3. 删 socks5 子表的 bandwidth_limit_mbps 列 (归属转移到 capacity)
ALTER TABLE resource_ip_pool_socks5 DROP COLUMN bandwidth_limit_mbps;

-- ============================================================================
-- 回滚 (慎用)
-- ============================================================================
-- ALTER TABLE resource_ip_pool_socks5 ADD COLUMN bandwidth_limit_mbps INT DEFAULT 0 AFTER log_level;
-- UPDATE resource_ip_pool_socks5 s
--   JOIN resource_ip_pool_capacity c ON c.ip_id = s.ip_id
--   SET s.bandwidth_limit_mbps = c.bandwidth_limit_mbps;
-- DROP TABLE resource_ip_pool_capacity;
