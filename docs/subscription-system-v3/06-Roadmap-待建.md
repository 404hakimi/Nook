# 06-Roadmap: 待建设计

> **本文集中所有"v3 设计意图明确、但代码尚未实现"的部分**: 套餐 / 订阅 / 订单 / 告警表 + allocator + 限速 enforce + throttle 状态机 + 故障切换 + 调度 Job。
>
> 现状已落地的内容在 [02-数据模型](./02-数据模型.md) / [03-业务核心](./03-业务核心-算法与流程.md) / [04-监控与流量](./04-监控-流量-告警-切换.md) / [05-Agent架构](./05-Agent架构与参数化.md)。本文所有内容默认 **❌ 未实现**, 是路线图而非现状。

---

## 0. 现状基线 (开始 roadmap 前已有什么)

| 已落地 | 说明 |
|---|---|
| 资源管理 | 线路机 / 落地机 CRUD + lifecycle 手动流转 + SSH 装机 (xray / dante / agent) |
| Agent 链路 | 心跳 / NIC 流量 / xray stats 上报 + task 队列 (6 种 task) + config 同步 |
| xray client | provision / revoke / rotate / sync 走 op 框架 (admin 手动指定落地机) |
| 危险操作编排 | OpOrchestrator (FIFO 串行 + watchdog + WS 进度) |
| 流量统计 | 用户层 (xray stats) + 机房层 (vnstat) 双轨采集 |

**roadmap 要补的本质**: 把"admin 手动一个个开客户端"升级成"用户自助下单 → 系统自动分配 + 部署 + 计费 + 故障自愈"。

---

## 1. 待建表 DDL

### 1.1 `trade_plan` — 套餐定义

```sql
CREATE TABLE trade_plan (
  id               CHAR(32)     PRIMARY KEY,
  code             VARCHAR(64)  UNIQUE NOT NULL              COMMENT 'jp_tyo_residential_100gb_monthly',
  name             VARCHAR(128) NOT NULL,
  region_code      VARCHAR(32)  NOT NULL                     COMMENT 'FK → system_region.code (展示分类)',
  ip_type          ENUM('RESIDENTIAL','ISP','DATACENTER') NOT NULL COMMENT '展示分类 (实际过滤走 trade_plan_resource)',
  traffic_gb       INT          NOT NULL                     COMMENT '月配额, 直接写 xray client totalBytes',
  bandwidth_mbps   INT                                       COMMENT '账面带宽 (商品页展示)',
  period_days      INT          NOT NULL DEFAULT 30,
  price_cny        DECIMAL(10,2) NOT NULL,
  cost_basis_cny   DECIMAL(10,2),
  enabled          TINYINT      NOT NULL DEFAULT 1,
  created_at       DATETIME     NOT NULL,
  updated_at       DATETIME     NOT NULL,
  INDEX idx_filter (region_code, ip_type, enabled)
);
```

**字段可变性**:
- 可改: `name` / `bandwidth_mbps` / `cost_basis_cny` / `enabled`
- **不可改**: `traffic_gb` / `period_days` / `region_code` / `ip_type` / `price_cny` — 改了会让历史订阅引用错位 → 一律建新 SKU + 老的 `enabled=0` 下架。

好处: `trade_subscription` 无需快照字段 (永远 JOIN 实时读); 历史订单流水记于 `trade_order_item.unit_price_cny`。

### 1.2 `trade_plan_resource` — SKU ↔ 资源池关联 (v3 核心)

```sql
CREATE TABLE trade_plan_resource (
  id              CHAR(32)     PRIMARY KEY,
  trade_plan_id     CHAR(32)     NOT NULL,
  resource_type   ENUM('FRONTLINE','LANDING') NOT NULL       COMMENT '门禁(线路机) / 公寓(落地机)',
  resource_id     CHAR(32)     NOT NULL                      COMMENT 'resource_server.id (按 type 区分角色)',
  enabled         TINYINT      NOT NULL DEFAULT 1            COMMENT 'admin 临时禁用 (老 sub 保留, 不接新)',
  created_at      DATETIME     NOT NULL,
  UNIQUE KEY uk_sku_res (trade_plan_id, resource_type, resource_id),
  INDEX idx_sku (trade_plan_id, resource_type, enabled)
);
```

> 注: resource_id 统一指向 `resource_server.id` (因为落地机现在也是 resource_server)。resource_type 区分门禁池 (frontline) / 公寓池 (landing)。

**语义**:
- 每个 SKU 关联多个 frontline (门禁池, 建议 ≥ 2 以便池内切换) + 多个 landing (公寓池, 决定容量)
- frontline 可跨 SKU 共享; landing 建议 SKU 独占

**容量计算** (一句 SQL, 砍掉 sold_traffic_gb 复杂度):
```sql
SELECT COUNT(*) AS remaining
FROM trade_plan_resource psr
JOIN resource_server s ON s.id = psr.resource_id
JOIN resource_server_landing l ON l.server_id = s.id
WHERE psr.trade_plan_id = ?
  AND psr.resource_type = 'LANDING'
  AND psr.enabled = 1
  AND s.lifecycle_state = 'LIVE'
  AND l.status = 'AVAILABLE';
```
容量 = LIVE landing 总数; 已售 = OCCUPIED; 剩余 = AVAILABLE。

### 1.3 `trade_subscription` — 订阅 (1 sub = 1 SKU = 1 落地机 = 1 线路机 = 1 xray_client)

```sql
CREATE TABLE trade_subscription (
  id                  CHAR(32)     PRIMARY KEY,
  member_user_id      CHAR(32)     NOT NULL,
  plan_id             CHAR(32)     NOT NULL                  COMMENT 'FK → trade_plan.id',
  xray_client_id      CHAR(32)     UNIQUE NOT NULL           COMMENT '1:1 → xray_client.id',
  started_at          DATETIME     NOT NULL,
  expires_at          DATETIME     NOT NULL,
  status              ENUM('ACTIVE','EXPIRED','CANCELLED') NOT NULL COMMENT '业务状态 (用户视角)',
  provision_state     ENUM('PROVISIONED','PROVISIONING','ERROR') NOT NULL DEFAULT 'PROVISIONED'
                                                            COMMENT '流程状态 (远端同步)',
  provision_error     VARCHAR(512),
  provision_retry     INT          NOT NULL DEFAULT 0,
  version             INT          NOT NULL DEFAULT 0        COMMENT '乐观锁',
  activated_at        DATETIME,
  expired_at          DATETIME,
  cancelled_at        DATETIME,
  created_at          DATETIME     NOT NULL,
  updated_at          DATETIME     NOT NULL,
  INDEX idx_member_active (member_user_id, status, provision_state),
  INDEX idx_expires (expires_at, status),
  INDEX idx_provision_error (provision_state)
);
```

**双状态机** (解耦):
- `status` (业务): ACTIVE / EXPIRED / CANCELLED
- `provision_state` (流程): PROVISIONED / PROVISIONING / ERROR
- 真正可用 = `status='ACTIVE' AND provision_state='PROVISIONED'`
- admin 异常订阅列表 = `provision_state IN ('PROVISIONING','ERROR')`

**节点切换靠订阅刷新, 不碰 DNS** (决策见 [07 §3.1.1](./07-决策-排除-风险-Checklist.md)): sub 不需要 sub_domain。用户订阅地址固定为 `nook.com/api/sub/{member.sub_token}`, 订阅接口动态返回该 sub 当前所在线路机的节点。故障切换 = 后台改 `xray_client.server_id` 迁到同 region 另一台线路机, 订阅接口下次就返回新 host; 用户客户端刷新订阅后恢复 (非 DNS 无感)。

### 1.4 `trade_order` + `trade_order_item` — 订单 (Sprint 3 接支付)

```sql
CREATE TABLE trade_order (
  id                 CHAR(32)     PRIMARY KEY,
  member_user_id     CHAR(32)     NOT NULL,
  amount_cny         DECIMAL(10,2) NOT NULL,
  status             ENUM('PENDING','PAID','REFUNDED','CANCELLED','EXPIRED') NOT NULL,
  reserve_expires_at DATETIME     NOT NULL                   COMMENT '库存预占截止 (created + 30min)',
  paid_at            DATETIME,
  version            INT          NOT NULL DEFAULT 0,
  created_at         DATETIME     NOT NULL,
  updated_at         DATETIME     NOT NULL,
  INDEX idx_member_status (member_user_id, status),
  INDEX idx_reserve_expires (reserve_expires_at, status)
);

CREATE TABLE trade_order_item (
  id                  CHAR(32)     PRIMARY KEY,
  order_id            CHAR(32)     NOT NULL,
  type                ENUM('NEW','RENEW') NOT NULL,
  plan_id             CHAR(32),                              -- type=NEW
  subscription_id     CHAR(32),                              -- type=RENEW
  quantity            INT          NOT NULL DEFAULT 1,
  unit_price_cny      DECIMAL(10,2) NOT NULL,
  amount_cny          DECIMAL(10,2) NOT NULL,
  reserved_frontline_id CHAR(32),                            -- NEW 时锁定门禁
  reserved_landing_id   CHAR(32),                            -- NEW 时锁定公寓
  created_at          DATETIME     NOT NULL,
  INDEX idx_order (order_id),
  CONSTRAINT chk_item_type_fields CHECK (
    (type='NEW'   AND plan_id IS NOT NULL AND subscription_id IS NULL
                  AND reserved_frontline_id IS NOT NULL AND reserved_landing_id IS NOT NULL)
    OR
    (type='RENEW' AND plan_id IS NULL AND subscription_id IS NOT NULL
                  AND reserved_frontline_id IS NULL AND reserved_landing_id IS NULL)
  )
);
```

CHECK 约束 DB 层兜底 type=NEW/RENEW 字段一致性。quantity>1 的 NEW item 拆成 quantity 条独立 item 各锁一对 (frontline, landing)。

### 1.5 `alert_record` — 系统告警

```sql
CREATE TABLE alert_record (
  id          CHAR(32)     PRIMARY KEY,
  type        ENUM('SKU_OUT_OF_STOCK','SERVER_TRAFFIC_HIGH','RESOURCE_EXPIRING','NODE_FAILOVER','AGENT_OFFLINE') NOT NULL,
  severity    ENUM('INFO','WARN','ERROR') NOT NULL,
  target_type VARCHAR(32),
  target_id   VARCHAR(64),
  message     TEXT         NOT NULL,
  resolved_at DATETIME                                       COMMENT 'null = 未处理',
  created_at  DATETIME     NOT NULL,
  INDEX idx_status (resolved_at, severity)
);
```

### 1.6 `resource_server_traffic` — NIC 流量周期历史

```sql
CREATE TABLE resource_server_traffic (
  id              CHAR(32)  PRIMARY KEY,
  server_id       CHAR(32)  NOT NULL,
  period_start    DATE      NOT NULL                        COMMENT 'BILLING_CYCLE=上次账单日, FIXED=安装日',
  rx_bytes        BIGINT    NOT NULL,
  tx_bytes        BIGINT    NOT NULL,
  total_bytes     BIGINT    NOT NULL,
  last_sampled_at DATETIME  NOT NULL,
  created_at      DATETIME  NOT NULL,
  updated_at      DATETIME  NOT NULL,
  UNIQUE KEY uk_server_period (server_id, period_start)
);
```

现状 `resource_server_capacity` 只存当前周期快照, 跨期归档需本表 + 周期切换 Job。落地机也是 resource_server, 复用此表 (server_id 即可)。

---

## 2. Allocator (资源分配器)

> 现状: 无 allocator, xray client provision 由 admin 在 dialog **手动指定落地机 ipId** (`ClientOpExecutor.doProvision` → `landingService.occupyById` CAS 占用)。roadmap 要做的是从 SKU 池自动选。

### 2.1 候选过滤 (SKU 池范围)

**落地机候选** (公寓):
```sql
SELECT s.id FROM resource_server s
JOIN resource_server_landing l ON l.server_id = s.id
JOIN trade_plan_resource psr ON psr.resource_id = s.id
WHERE psr.trade_plan_id = ? AND psr.resource_type = 'LANDING' AND psr.enabled = 1
  AND s.lifecycle_state = 'LIVE'
  AND l.status = 'AVAILABLE'
LIMIT 1 FOR UPDATE;   -- 行锁防并发抢占
```

**线路机候选** (门禁, 选客户数最少):
```sql
SELECT s.id FROM resource_server s
JOIN trade_plan_resource psr ON psr.resource_id = s.id
JOIN resource_server_capacity cap ON cap.server_id = s.id
WHERE psr.trade_plan_id = ? AND psr.resource_type = 'FRONTLINE' AND psr.enabled = 1
  AND s.lifecycle_state = 'LIVE'
  AND cap.throttle_state = 'NORMAL'                          -- ❌ 依赖 throttle 状态机
  AND (cap.client_max_count = 0
       OR cap.client_max_count > (SELECT COUNT(*) FROM xray_client WHERE server_id = s.id AND status = 1))
ORDER BY (SELECT COUNT(*) FROM xray_client WHERE server_id = s.id AND status = 1) ASC
LIMIT 1;
```

> 带宽链路守恒校验 (§6.3) 在此处叠加: 该线路机已挂落地机 Σ限速 + 候选落地机限速 ≤ 线路机限速 × 0.9。

### 2.2 选择策略 (起步极简)

1. 落地机: SKU 池里第一个 AVAILABLE (按 id 升序)
2. 线路机: 客户数最少的 LIVE + NORMAL

复杂评分 / 智能调度留到 Sprint 5+。

### 2.3 单步分配伪代码 (Sprint 1)

> **Sprint 1 admin 代客版** (op 框架同步 + 复用 provision) 详见 [08-Sprint1 §5](./08-Sprint1-套餐订阅-实施设计.md)。下面是 **portal 自助版** (Sprint 3, agent task 异步驱动 + provision_state)。**无 CF DNS** — 节点切换靠订阅刷新 (§4)。

```java
Subscription allocate(memberId, planSkuId) {
  PlanSku sku = planSkuMapper.selectById(planSkuId);
  if (!sku.enabled) throw new BusinessException(SKU_DISABLED);

  // 第 1 阶段: DB 短事务
  AllocateResult ar = tx.execute(status -> {
    var landing = landingMapper.findFirstAvailableInSkuPool(planSkuId);  // FOR UPDATE
    if (landing == null) throw new BusinessException(SKU_OUT_OF_STOCK);
    var frontline = serverMapper.findBestFrontlineInSkuPool(planSkuId);
    if (frontline == null) throw new BusinessException(NO_AVAILABLE_SERVER);
    if (landingMapper.tryOccupy(landing.id, "AVAILABLE", memberId) == 0)
      throw new BusinessException(IP_RACE_LOST);            // 上层重试

    String uuid = UUID.randomUUID().toString();
    var client = insertXrayClient(frontline.id, landing.id, memberId, uuid);
    var sub = insertSubMain(memberId, sku, client.id, ACTIVE, PROVISIONING);
    return new AllocateResult(sub, frontline, landing, client);
  });

  // 第 2 阶段: 远端开通 (事务外; 无 CF DNS)
  try {
    agentTask.enqueue(ar.frontline.id, "xray_provision_user", provisionPayload(ar, sku));
    subMapper.markProvisioned(ar.sub.id);
  } catch (Exception e) {
    subMapper.markProvisionError(ar.sub.id, e.getMessage());  // 不回滚, 等 admin
    throw new BusinessException(PROVISION_FAILED, ar.sub.id);
  }
}
```

**关键**: 业务 `status=ACTIVE` 付费即定; 流程 `provision_state` 走 PROVISIONING → PROVISIONED / ERROR; 失败标 ERROR 不自动回滚 (避免远端半成品 + DB 回滚不一致), 留 admin 介入。

---

## 3. 订阅生命周期

### 3.1 状态机 (无宽限期)

```
[新购] → ACTIVE ──(expires_at 到)──→ EXPIRED ──(14 天没续)──→ CANCELLED
            ↑                            │
            └──── 续费 (EXPIRED 期重新部署) ┘
```

- ACTIVE 期续费: 仅延长 expires_at + xray `adu` 重填 totalBytes (轻量)
- EXPIRED 期续费 (14 天内): **重新部署 client** (已 revoke) + 恢复 IP 占用
- 14 天宽限给 IP 保留, 不是服务可用: EXPIRED 期间用户连不上, 但 IP 还属于他

### 3.2 周期计算

- 新购: `started_at = now`, `expires_at = now + period_days`
- ACTIVE 续费: `expires_at = max(expires_at, now) + period_days`
- EXPIRED 续费: 视为重启, `started_at = now`, 必须重新部署 client
- 每 sub 独立周期 (同用户 N 套餐 N 个到期日)

---

## 4. 订阅 URL (用户级聚合, 订阅刷新驱动)

### 4.1 设计: 订阅地址做间接层, 不碰 DNS

```
固定不变: https://nook.com/api/sub/{member.sub_token}   ← 用户只存这一个, 可重置 token
            ↓ 客户端定时 / 手动刷新
后端动态返回: 该会员所有 ACTIVE sub 的当前 vmess 节点列表
            ↓ 每个节点 host = 该 sub 当前所在线路机的固定域名 (xray_config.domain)
```

- `member_user.sub_token` ✅ DDL 已有 (端点未建); 用户可在 portal 重置 token (一键失效旧地址)
- vmess host 用**线路机级固定域名** (每台线路机一个, 装机时配 A 记录指向该机 IP, 数量 = 线路机数, 个位数), **不用 per-sub 子域名**
- **不依赖 Cloudflare per-sub DNS**: 切换 / 换线路机后订阅接口下次返回新 host, 客户端刷新订阅即恢复

### 4.2 节点切换 (订阅刷新驱动, 替代 DNS 切换)

```
① 选同 region / SKU 池内另一台 frontline (排除故障机)
② 派任务给新 frontline: provision user (uuid/email 不变, totalBytes=剩余)
③ UPDATE xray_client.server_id = 新 frontline
④ 派任务给旧 frontline: rmu (best effort)

用户感知: 客户端下次刷新订阅 → 拿到新 host (新线路机域名) → 连上
         (非 DNS 无感; 靠客户端"自动更新订阅"周期 或 用户手动刷新触发)
缓解: 客户端导入时引导设自动更新订阅间隔 + 切换后邮件/站内信提示"请更新订阅"
```

> 决策见 [07 §3.1.1](./07-决策-排除-风险-Checklist.md): 放弃 per-sub 子域名 + CF DNS 切换 (订阅数量级 DNS 记录难管理 + 强依赖 CF), 改订阅刷新。trade-off: 故障恢复非无感, 要客户端刷订阅。

### 4.3 节点备注 (vmess ps 字段)

```
"{flag} {region} | {ip_type} | {traffic}GB | 到期 {MM-DD}"
例: "🇯🇵 东京 | 住宅 | 100GB | 到期 06-15"
```

---

## 5. 业务流程 (新购 / 续费 / 退订)

### 5.1 新购 (Sprint 3: 三段式锁库存 + 支付 + 激活)

```
[结算-锁库存] POST /api/orders
  对每条 NEW item: reserveForOrder (抢 landing RESERVED + 锁 frontline)
  INSERT trade_order (PENDING, reserve_expires_at = now + 30min) + trade_order_item × N
  任一失败整单回滚 + 已 reserve 释放

[支付] Stripe webhook → activateForItem
  landing RESERVED → OCCUPIED; provision xray_client + 建 trade_subscription (复用 op 框架; 无 CF DNS)

[通知] 邮件: 订单已激活 + 订阅 URL
```

异常: 抢不到 → 整单失败; 30min 未付 → OrderTimeoutCleanupJob 释放; 激活失败 → provision_state=ERROR + retry/admin。

### 5.2 续费

- ACTIVE 期 (轻量): 校验 plan enabled → expires_at += period → xray `adu` 重填 totalBytes
- EXPIRED 期 (重部署): 校验 14 天内 → 完整 provision (adu+adr+ado) → status=ACTIVE
- 两条路径价格都取当前 `trade_plan.price_cny`

### 5.3 SKU 上下架

```
[新增 SKU] 填字段 → 保存 (enabled=0) → 绑定资源 (选 frontline + landing) → INSERT trade_plan_resource → 上架 enabled=1
[加货] SKU 编辑 → 新增 landing 到池 → INSERT trade_plan_resource
[下架] enabled=0; 老用户禁止续费, 使用不受影响自然 EXPIRED
```

---

## 6. 限速 enforce (双层 + 链路守恒)

> 现状: `resource_server_capacity.bandwidth_limit_mbps` 字段存在但无代码读; 落地机限速字段**未加**; agent Go 端**完全无 tc / dante 限速代码**。本节是完整待建方案。

### 6.1 链路模型 (容量守恒)

```
用户 ─vmess+ws+TLS─→ [线路机 xray] ─socks5─→ [落地机 dante] ─→ Internet
                          ↓                       ↓
                      tc 出站限速             dante bandwidth class
                      (服务器级)              (per-IP = per-客户, v3 1:1)
```

**核心约束**: 同一线路机上挂的所有落地机限速之和 ≤ 线路机限速 × (1 - 预留率)。
预留率默认 10% (`BANDWIDTH_RESERVATION_RATIO = 0.10`):
```
Σ(挂在 server X 的 landing.bandwidth_limit_mbps) ≤ server X.bandwidth_limit_mbps × 0.9
```

**为什么不用 xray 客户级限速**: xray-core 原生不支持 per-user bandwidth throttle。

### 6.2 限速实施层

| 层 | 实施方 | 颗粒度 | 配置方式 |
|---|---|---|---|
| 落地机 | landing agent → dante | per-IP (= per-客户) | sockd.conf `bandwidth class` + restart danted |
| 线路机 | frontline agent → tc | 整个出站接口 | `tc qdisc replace dev eth0 root tbf rate Xmbit burst 100mb latency 50ms` |

### 6.3 双层容量校验

| 时机 | 类型 | 公式 | 失败 |
|---|---|---|---|
| admin 改限速 / 加 landing 到 SKU 池 | 静态 (松) | Σ(SKU 池 landing 限速) ≤ min(SKU 池 frontline 限速) × 0.9 | 拒绝操作 + 弹超载数值 |
| allocator 选 server | 动态 (紧) | 该 server 已挂 Σ限速 + 候选限速 ≤ server 限速 × 0.9 | allocator 跳过该 server |

### 6.4 限速变更链路 (agent task)

```
admin 改 landing.bandwidth_limit_mbps = 50
  ↓ PUT 限速接口 → Validator 静态校验 → UPDATE landing
  ↓ 派 task: agent_task (agent_type=landing, source_id=landingId,
              task_type=socks5_set_bandwidth, payload={mbps:50})
落地机 agent 轮询拉到 (≤ 60s)
  → 改 sockd.conf bandwidth class → systemctl restart danted → 上报 SUCCESS
```

线路机走 `server_set_bandwidth` task, executor 跑 `tc qdisc replace`。

### 6.5 待建清单

| 项 | 说明 |
|---|---|
| `resource_server_landing.bandwidth_limit_mbps` 字段 | DDL ADD COLUMN |
| 静态链路校验 Validator | admin 改限速 / 加 landing 到 SKU 池时 |
| 动态链路校验 | allocator 选 server 时 |
| `socks5_set_bandwidth` task + Go executor | dante bandwidth class 写 sockd.conf + restart |
| `server_set_bandwidth` task + Go executor | tc qdisc 命令封装 |
| agent 启动 reconcile | tc 不持久化, 重启后需重配 |

---

## 7. throttle 状态机 + 容量监控

> 现状: `throttle_state` 只初始化 NORMAL, 无 Job 翻转。

### 7.1 状态转换 (`ServerCapacityStateJob`, 每 5min)

| 当前 | 触发 | 新状态 | 告警 |
|---|---|---|---|
| NORMAL | used/quota ≥ 0.90 且 < 1.00 | THROTTLED | SERVER_TRAFFIC_HIGH WARN |
| NORMAL | used/quota ≥ 1.00 | THROTTLED | SERVER_TRAFFIC_HIGH ERROR |
| THROTTLED | used/quota < 0.90 (周期重置后) | NORMAL | resolve 告警 |
| THROTTLED | ≥ 1.00 且当前 WARN | THROTTLED | 升级 severity → ERROR |

幂等; THROTTLED 的 server 被 allocator 跳过 (§2.1)。

### 7.2 周期切换 (`resource_server_traffic` 历史归档)

NIC 上报时检测 `period_start` 漂移 → UPSERT 上周期到历史表 + 当前 capacity 行归零。按 `quota_reset_policy` (BILLING_CYCLE / FIXED) 算 period_start。

---

## 8. 告警系统 (`alert_record`)

### 8.1 5 种类型

| type | severity | 触发 |
|---|---|---|
| SKU_OUT_OF_STOCK | WARN | SKU 池 AVAILABLE landing = 0, 购买被拒 |
| SERVER_TRAFFIC_HIGH | WARN/ERROR | 线路机 used/quota ≥ 90% |
| RESOURCE_EXPIRING | INFO | server `expires_at - 7d ≤ now` |
| NODE_FAILOVER | ERROR | 心跳 ≥ 5min 真故障切换 / admin 手动 |
| AGENT_OFFLINE | WARN | 心跳 1-3min 超时 (未触发切换) |

### 8.2 渠道 + 去重

- 后台: admin 首页 `WHERE resolved_at IS NULL ORDER BY severity DESC`
- 邮件: severity ≥ WARN 触发时发
- 去重: 同 type + target 24h 内未处理不重复

---

## 9. 故障切换

> 现状: `AgentHeartbeatTimeoutJob` 已做 1/3/5min 分级 + `temp_unhealthy=1` 自动标; 但 **≥5min 真切换 (`onServerFault`) 未实现**, 心跳恢复 resolve 也未实现。

### 9.1 心跳分级 (现状 ✅ 部分)

| 超时 | 动作 | 状态 |
|---|---|---|
| 1-3min | AGENT_OFFLINE WARN 告警 | ❌ (依赖 alert_record) |
| 3-5min | temp_unhealthy=1 (allocator 跳过) | ✅ |
| ≥ 5min | 真故障, 池内切换 | ❌ |

### 9.2 池内切换 (`onServerFault`, ❌)

故障 server 上每个 sub, 在该 sub 的 SKU 池内 (天然同 region, 因 SKU 绑定同地区资源) 挑其他 frontline 切过去, 走 §4.2 订阅刷新切换 (**无 CF DNS**)。池内全 THROTTLED → 紧急 fallback (force=true) + ERROR 告警; 池里没备用 frontline → ERROR 告警 (用户需等故障机恢复)。

### 9.3 admin 手动换 IP (❌)

落地 IP 用户独享, **系统不自动换**, admin 主动决策:
```
派 task 改 outbound (xray_update_outbound, 改 danteEndpoint)
→ UPDATE xray_client.ip_id → 旧 landing OCCUPIED→COOLING, 新 landing AVAILABLE→OCCUPIED
用户出口 IP 秒级切换 (host 不变)
```

---

## 10. 调度 Job 全清单

> 现状仅 `AgentHeartbeatTimeoutJob` (✅) + agent push 接收 (✅)。其余全 ❌。

### 10.1 状态维护类

| Job | Cron | 职责 |
|---|---|---|
| `AgentHeartbeatTimeoutJob` ✅ | 1min | 心跳分级 1/3/5min + temp_unhealthy |
| `ServerCapacityStateJob` ❌ | 5min | throttle_state NORMAL↔THROTTLED |
| `SubscriptionExpireJob` ❌ | 日 00:30 | ACTIVE 且 expires_at < now → EXPIRED + revoke |
| `SubscriptionCancelJob` ❌ | 日 02:00 | EXPIRED 且 +14d < now → CANCELLED + landing COOLING (无 CF DNS) |
| `LandingCoolingReleaseJob` ❌ | 30min | COOLING 且 cooling_until < now → AVAILABLE |
| `OrderTimeoutCleanupJob` ❌ | 5min | PENDING 且 reserve_expires_at < now → EXPIRED + 释放 (Sprint 3) |

### 10.2 告警评估类

| Job | Cron | 职责 |
|---|---|---|
| `ServerTrafficAlertJob` ❌ | 5min (StateJob 后) | raise/upgrade/resolve SERVER_TRAFFIC_HIGH |
| `ResourceExpiringAlertJob` ❌ | 日 09:00 | server expires_at - 7d → RESOURCE_EXPIRING |

### 10.3 用户通知类

| Job | Cron | 职责 |
|---|---|---|
| `UserQuotaNearLimitNotifyJob` ❌ | 5min | client(up+down) ≥ 80% totalBytes → 邮件 |
| `SubscriptionRenewalReminderJob` ❌ | 日 09:00 | expires_at - 7d → 邮件提醒 |
| `SubscriptionExpiredNotifyJob` ❌ | 日 01:00 | 当天 EXPIRED → 邮件 (14 天保留原 IP) |

---

## 11. lifecycle 自动推进 (现状全手动)

> 现状: `transition-lifecycle` 接口手动切; 落地机 SOCKS5 装机收尾 `→ LIVE`; 线路机 xray 装机**不切** (停在 INSTALLING)。

**目标**: 每步操作完成后端自动推进, 手动切换降级为生产兜底。

| 角色 | → READY 触发 | → LIVE 触发 |
|---|---|---|
| 线路机 | xray 装机收尾 (`persistDeployment` 末尾) | agent 首次心跳 (lifecycle=READY 时升一次) |
| 落地机 | SOCKS5 装机收尾 | agent 首次心跳 |

> 落地机现状直接 INSTALLING → LIVE, 改造后应改成 → READY 再由心跳推 → LIVE, 跟线路机对称。

---

## 12. Sprint 排期建议

| Sprint | 内容 |
|---|---|
| **1** | `trade_plan` + `trade_plan_resource` + `trade_subscription` DDL; admin SKU 管理 UI; allocator 单步分配; admin 代客下单 (复用 provision); 订阅 URL 端点 (线路机域名); 详见 [08-Sprint1](./08-Sprint1-套餐订阅-实施设计.md) |
| **1.5** | 限速 enforce (落地机限速字段 + dante/tc agent executor + 链路校验) |
| **2** | throttle 状态机 + 周期切换 + 告警系统 (`alert_record`) + 故障切换 (`onServerFault`) |
| **3** | `trade_order`/`trade_order_item` + Stripe 支付 + 三段式锁库存; portal 购买/续费 + 订阅 URL 端点 |
| **5+** | mesh 多备份 / 流量包加购 / 自动迁移 / 复杂评分 / 自研客户端 |

---

## 13. 排除项 (起步不做)

| 项 | 备注 |
|---|---|
| Mesh 多备份 | v3 用 1 sub 1 server + 订阅刷新切换替代 |
| per-sub 子域名 + CF DNS 切换 | 砍掉; 订阅刷新驱动切换 (§4) + 线路机级固定域名 |
| 跨实例配额聚合 enforce | 单 server xray totalBytes 准确 |
| 流量包加购 | Sprint 5+ |
| 用户自定义套餐 | admin 预定义 |
| CDN 套代理 (CF Proxied) | 直连 server IP 避开 ToS |
| 套餐升降级 (中途换 SKU) | 买新套餐 + 旧的不续费 |
| 多区域降级分配 | 硬拒 |
