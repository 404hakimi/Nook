-- ===================================================================
-- Option 2: 删 resource_server_credential.host, 用 resource_server.ip_address 作 canonical
-- ===================================================================
--
-- 背景:
--   - 历史上 credential.host 跟 server.ip_address 各自维护, 99% 场景两者相同
--   - 无 bastion / 跳板机场景, 双字段纯属冗余 (drift 风险 + 字段同义)
--
-- 新规则:
--   - server.ip_address = 服务器对外身份 (UI 显示 + SSH 连接目标 + 业务标识)
--   - credential 仅 AUTH (port / user / password / timeouts)
--
-- 执行顺序 (按代码切换节奏):
--   1. 跑这个 SQL: 复制 host → ip_address (为 NULL 的行) + drop host 列
--   2. 部署后端新代码 (DO 移除 host 字段)
--   3. 部署前端新代码 (UI 移除 SSH 主机字段, 用 IP 地址替代)
--
-- 回滚:
--   - 加回 host 列, 从 ip_address 反向复制即可 (数据是兼容的)
-- ===================================================================

-- 1. 把现有 credential.host 复制到 server.ip_address (仅 ip_address 缺值时)
UPDATE resource_server s
JOIN resource_server_credential c ON c.server_id = s.id
SET s.ip_address = c.host
WHERE s.ip_address IS NULL OR s.ip_address = '';

-- 2. 校验: 不允许有 server 没 ip_address (如果有,代表 credential 也是空的, 需要手工修)
SELECT id, name, server_type, lifecycle_state
FROM resource_server
WHERE ip_address IS NULL OR ip_address = '';
-- ↑ 如果上面 SELECT 返回行, 停下来手工补 ip_address 再继续

-- 3. ip_address 加 NOT NULL 约束
ALTER TABLE resource_server
MODIFY COLUMN ip_address VARCHAR(128) NOT NULL COMMENT '出网真实 IP / 域名 (= SSH 连接目标)';

-- 4. drop credential.host 列
ALTER TABLE resource_server_credential
DROP COLUMN host;
