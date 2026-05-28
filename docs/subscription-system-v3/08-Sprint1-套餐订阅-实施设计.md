# 08-Sprint 1: 套餐订阅实施设计

> **目标**: 在现有资源管理 + xray client 开通能力之上, 补齐"套餐 → 订阅"最小闭环, 让 admin 能代客下单, 用户能用订阅 URL 连上。
>
> **本文是实施设计 (评审稿)**, 评审通过后再编码。颗粒度到表 DDL / 接口 / 流程伪代码 / 实施步骤。
> 上层蓝图见 [06-Roadmap](./06-Roadmap-待建.md), 现状基线见 [02-数据模型](./02-数据模型.md) / [03-业务核心](./03-业务核心-算法与流程.md)。

---

## 1. 范围与边界

### 1.1 Sprint 1 做什么

| # | 能力 | 说明 |
|---|---|---|
| 1 | **套餐管理** | admin 建 `trade_plan` + 关联资源池 `trade_plan_resource` (绑线路机 + 落地机) + 看容量 |
| 2 | **订阅记录** | `trade_subscription` 表 (会员 ↔ 套餐 ↔ xray_client, 到期时间, 状态) |
| 3 | **allocator** | 从 SKU 池自动选 1 落地机 + 1 线路机, **复用现有 provision** |
| 4 | **admin 代客下单** | admin 选会员 + SKU → 自动分配 + 开通 + 建订阅 |
| 5 | **订阅 URL** | 会员聚合订阅端点 (sub_token → 该会员所有 ACTIVE sub 的 vmess) |
| 6 | **到期 / 退订** | 到期 Job 自动 revoke + admin 手动退订 |

### 1.2 Sprint 1 明确不做 (简化决策)

| 不做 | 原因 / 何时做 |
|---|---|
| portal 用户自助购买 | admin 代客下单先跑通闭环; portal → Sprint 3 |
| Stripe 支付 + 订单表 (trade_order/trade_order_item) | → Sprint 3 |
| **per-sub 独享子域名 + Cloudflare** | **已砍掉 (非简化, 是正式方案)**: vmess host 用线路机级固定域名 (`xray_config.domain`); 节点切换靠订阅刷新, 不碰 DNS (决策见 [07 §3.1.1](./07-决策-排除-风险-Checklist.md)) |
| 14 天 IP 保留期 | Sprint 1 到期直接 revoke + 落地机释放; 保留期 → Sprint 2 |
| 限速 enforce (dante/tc) | → Sprint 1.5 |
| throttle 状态机 / 告警 / 故障切换 | → Sprint 2 |
| 乐观锁 version / provision_state 双状态机 | admin 代客是同步 op (submitAndWait), 成功才建 sub; 不需要异步流程态。Sprint 3 接支付时补 |

### 1.3 三个关键简化的影响

1. **线路机固定域名 (正式方案)**: vmess URL 的 host = 该 client 所在线路机的 `xray_config.domain`。一个会员的多个 sub 若落在不同线路机就是不同 host。换线路机后订阅接口返回新 host, **用户刷新订阅即恢复** (非重新导入; 靠客户端自动更新订阅周期或手动刷新, 非 DNS 无感)。
2. **admin 代客下单**: 同步执行 (op 框架 submitAndWait), provision 成功才建 trade_subscription, 失败整体回滚不留垃圾 sub。**省掉** PROVISIONING/ERROR 流程态。
3. **到期直接 revoke**: 不保留 IP。到期 Job 调 revoke (落地机 → COOLING → 自动回 AVAILABLE)。

---

## 2. 模块划分

新建 **`nook-module-trade`** (`-api` + `-server`), 装套餐 (`trade_plan` / `trade_plan_resource`) + 订阅 (`trade_subscription`) + 未来订单 (`trade_order` / `trade_order_item`)。表前缀统一 `trade_`。交易域职责: "卖什么 + 谁买了 + 到期管理"。

**跨模块调用** (走 `-api` 契约, 不直接注入对方 Service):
- trade → node: 触发开通/吊销 → node 需新增 `XrayClientProvisionApi` (provision / revoke, 包装现有 op 框架调用) 到 `nook-module-node-api`
- trade → node: 查 SKU 池候选 (落地机 AVAILABLE / 线路机客户数) → node 新增/复用资源查询 Api
- trade → system: 查 region / ip_type 字典 (校验 SKU 字段)
- trade → member: 查会员 (代客下单校验 active)

> 远端副作用 (xray 三段下发 / 落地机占用) 仍由 node 的 op 框架执行; trade 只通过 `-api` 触发 + 管订阅生命周期。
> **新增成本**: 要在 `nook-module-node-api` 补 `XrayClientProvisionApi` 契约 (现有 provision 只有内部 Service, 未暴露 api)。node-api 已存在 (agent 模块在用 `ResourceServerApi`), 加一个契约顺理成章。

---

## 3. 数据模型

### 3.1 `trade_plan` — 套餐定义

```sql
CREATE TABLE trade_plan (
  id               CHAR(32)     PRIMARY KEY,
  code             VARCHAR(64)  UNIQUE NOT NULL              COMMENT 'jp_tyo_residential_100gb_monthly',
  name             VARCHAR(128) NOT NULL,
  region_code      VARCHAR(32)  NOT NULL                     COMMENT 'FK → system_region.code (展示分类)',
  ip_type_id       CHAR(32)                                  COMMENT 'FK → system_ip_type.id (展示分类)',
  traffic_gb       INT          NOT NULL                     COMMENT '月配额, 写 xray client totalBytes',
  bandwidth_mbps   INT                                       COMMENT '账面带宽 (商品页展示, Sprint 1 不 enforce)',
  period_days      INT          NOT NULL DEFAULT 30,
  limit_ip         INT          NOT NULL DEFAULT 0           COMMENT '同时连接 IP 数, 写 xray limitIp; 0=不限',
  price_cny        DECIMAL(10,2) NOT NULL,
  cost_basis_cny   DECIMAL(10,2),
  enabled          TINYINT      NOT NULL DEFAULT 1,
  remark           VARCHAR(255),
  created_at       DATETIME     NOT NULL,
  updated_at       DATETIME     NOT NULL,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_filter (region_code, ip_type_id, enabled)
);
```

**字段可变性** (Validator 强制): 可改 `name` / `bandwidth_mbps` / `cost_basis_cny` / `enabled` / `remark`; **不可改** `traffic_gb` / `period_days` / `region_code` / `ip_type_id` / `price_cny` / `limit_ip` (改了破历史订阅引用 → 建新 SKU + 旧的 enabled=0)。

> 无 DB 外键 (项目规范), region/ip_type 引用由 Validator 校验。

### 3.2 `trade_plan_resource` — SKU ↔ 资源池

```sql
CREATE TABLE trade_plan_resource (
  id              CHAR(32)     PRIMARY KEY,
  trade_plan_id     CHAR(32)     NOT NULL,
  resource_type   ENUM('FRONTLINE','LANDING') NOT NULL       COMMENT '门禁(线路机) / 公寓(落地机)',
  resource_id     CHAR(32)     NOT NULL                      COMMENT 'resource_server.id',
  enabled         TINYINT      NOT NULL DEFAULT 1            COMMENT '临时禁用 (老 sub 保留不接新)',
  created_at      DATETIME     NOT NULL,
  UNIQUE KEY uk_sku_res (trade_plan_id, resource_type, resource_id),
  INDEX idx_sku (trade_plan_id, resource_type, enabled)
);
```

每个 SKU 关联 ≥1 frontline (建议 ≥2 备切换) + ≥1 landing (决定容量)。

**容量计算**:
```sql
-- 剩余 = SKU 池里 LIVE + AVAILABLE 的落地机数
SELECT COUNT(*) FROM trade_plan_resource psr
JOIN resource_server s ON s.id = psr.resource_id AND s.deleted = 0
JOIN resource_server_landing l ON l.server_id = s.id
WHERE psr.trade_plan_id = ? AND psr.resource_type = 'LANDING' AND psr.enabled = 1
  AND s.lifecycle_state = 'LIVE' AND l.status = 'AVAILABLE';
```

### 3.3 `trade_subscription` — 订阅 (Sprint 1 极简版)

```sql
CREATE TABLE trade_subscription (
  id              CHAR(32)     PRIMARY KEY,
  member_user_id  CHAR(32)     NOT NULL,
  plan_id         CHAR(32)     NOT NULL                      COMMENT 'FK → trade_plan.id',
  xray_client_id  CHAR(32)     UNIQUE NOT NULL               COMMENT '1:1 → xray_client.id',
  started_at      DATETIME     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  status          ENUM('ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  INDEX idx_member_status (member_user_id, status),
  INDEX idx_expires (expires_at, status)
);
```

> **永久不含** `sub_domain` / `cf_record_id` (订阅刷新切换替代 DNS, 见 [07 §3.1.1](./07-决策-排除-风险-Checklist.md))。Sprint 1 也不含 `provision_state` / `version` (同步下单), 这两个 Sprint 3 接支付时按需 ALTER ADD。
> sub ↔ xray_client 1:1; client 的 server_id (线路机) / ip_id (落地机) / totalBytes / expiry 都在 xray_client + 远端 xray, trade_subscription 只记订阅层 (套餐 + 周期 + 状态)。

---

## 4. 套餐管理 (admin)

### 4.1 接口 (`PlanSkuController` @ `/admin/plan/sku`)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page-sku` | 分页 (region/ip_type/enabled 过滤) + 每行带容量 (总/已售/剩余) |
| GET | `/get-sku` | 详情 + 已关联资源列表 |
| POST | `/create-sku` | 建 SKU (默认 enabled=0) |
| PUT | `/update-sku` | 改可变字段 (Validator 拦不可变字段) |
| POST | `/toggle-enabled` | 上/下架 |
| DELETE | `/delete-sku` | 软删 (有活跃 sub 时拒) |
| POST | `/bind-resource` | 关联 frontline / landing (校验 LIVE; landing 校验 ip_type 匹配) |
| POST | `/unbind-resource` | 解绑 (enabled=0, 老 sub 不受影响) |
| GET | `/list-resource` | 列 SKU 已关联资源 + 各自状态 |

### 4.2 绑定校验 (`PlanSkuResourceValidator`)

- frontline: `resource_server.server_type=frontline` 且 `lifecycle_state=LIVE`
- landing: `server_type=landing` 且 `lifecycle_state=LIVE`, 且 `ip_type_id` 跟 SKU 的 `ip_type_id` 一致
- 去重: `uk_sku_res` 兜底 + Validator 友好提示

---

## 5. allocator + admin 代客下单

### 5.1 核心: allocator 不重做开通, 只选址 + 复用 provision

现有 `XrayClientService.provision(reqVO)` (走 op 框架 CLIENT_PROVISION) 已能"给定 serverId + ipId 开客户端 + occupy 落地机 CAS"。allocator 增量 = **从 SKU 池选 serverId + ipId**。

### 5.2 新增查询 (Mapper)

```sql
-- 选落地机: SKU 池里第一个 AVAILABLE (按 created_at 升序, 简单稳定)
findFirstAvailableLandingInSkuPool(planSkuId) → resource_server.id  (LIMIT 1)

-- 选线路机: SKU 池里客户数最少的 LIVE frontline
findBestFrontlineInSkuPool(planSkuId) → resource_server.id
  ORDER BY (SELECT COUNT(*) FROM xray_client WHERE server_id = s.id AND status = 1) ASC
  + 满足 capacity.client_max_count (0 或 未超)
```

> allocator **不自己 occupy 落地机**, 只选候选 id 传给 provision; provision 内 `occupyById` CAS 兜底并发抢占, 失败抛异常 → 上层重试选下一个 (最多重试池内候选数次)。

### 5.3 admin 代客下单流程 (`SubscriptionService.adminCreate`)

```java
SubMain adminCreate(memberUserId, planSkuId) {
  PlanSku sku = planSkuValidator.validateEnabled(planSkuId);
  memberValidator.validateActive(memberUserId);

  // 选址 (带重试: 落地机被并发抢则换下一个)
  String frontlineId = allocator.pickFrontline(planSkuId);   // 无 → NO_AVAILABLE_SERVER
  for (重试 ≤ N) {
    String landingId = allocator.pickLanding(planSkuId, 已试过的);  // 无 → SKU_OUT_OF_STOCK
    try {
      // 复用现有 provision (op 框架 submitAndWait, 同步等开通完成)
      XrayClientProvisionReqVO req = new XrayClientProvisionReqVO();
      req.setServerId(frontlineId);
      req.setIpId(landingId);
      req.setMemberUserId(memberUserId);
      req.setTotalBytes(sku.trafficGb * 1024L*1024*1024);     // 0 = 不限
      req.setExpiryEpochMillis(now + sku.periodDays * 86400_000L);
      req.setLimitIp(sku.limitIp);
      String clientId = xrayClientService.provision(req);     // occupy landing CAS 在内部

      // 建订阅 (provision 成功才建; 失败上面抛异常, 不会到这)
      SubMain sub = SubMain.builder()
          .memberUserId(memberUserId).planId(sku.id).xrayClientId(clientId)
          .startedAt(now).expiresAt(now + sku.periodDays * 86400_000L)
          .status(ACTIVE).build();
      subMainMapper.insert(sub);
      return sub;
    } catch (LandingRaceLostException | DuplicateKeyException e) {
      continue;   // 落地机被抢, 换一个
    }
  }
  throw new BusinessException(SKU_OUT_OF_STOCK);
}
```

**事务边界**: provision 自身是 op 框架内事务 (occupy + insert client + 远端三段 saga)。trade_subscription insert 在 provision **成功返回后** 单独插入。若 sub insert 失败 (极少), 需补偿 revoke client — Sprint 1 可先靠 `xray_client` 无对应 sub 的孤儿对账 (admin 列表能看出来), 不做自动补偿。

### 5.4 接口 (`SubscriptionController` @ `/admin/sub`)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin-create` | admin 代客下单 (memberUserId + planSkuId) |
| GET | `/page-sub` | 订阅分页 (会员/SKU/状态过滤; 带流量已用/到期) |
| GET | `/get-sub` | 详情 (含 client/landing/frontline + vmess 预览) |
| POST | `/renew` | 续费 (延长 expires_at + xray adu 重填 totalBytes) |
| POST | `/cancel` | admin 退订 (revoke client + status=CANCELLED) |

---

## 6. 订阅 URL (聚合, 线路机固定域名)

### 6.1 端点 (`SubUrlController`, member token 鉴权或公开 token)

```
GET /api/sub/{sub_token}
  member = memberMapper.findByToken(token)        // 404 if null
  subs = subMainMapper.findActiveByMember(member.id)   // status=ACTIVE
  nodes = subs.map(buildVmess)
  return Base64(join("\n", nodes))                // text/plain
         + header Subscription-Userinfo (流量/到期, 可选)
```

### 6.2 拼 vmess (host = 线路机固定域名)

```java
String buildVmess(SubMain sub) {
  XrayClientDO c = xrayClientMapper.selectById(sub.xrayClientId);
  XrayConfigDO cfg = xrayConfigMapper.selectById(c.serverId);   // 线路机 inbound 配置
  // Sprint 1: host 直接用线路机固定域名; 无 domain 则退化用 server IP
  String host = StrUtil.isNotBlank(cfg.domain) ? cfg.domain : serverIp(c.serverId);
  return vmess({
    add: host,
    port: cfg.sharedInboundPort,     // 默认 443
    id: c.clientUuid,
    net: cfg.transport,              // ws
    path: cfg.wsPath,
    tls: cfg.tlsCertPath != null ? "tls" : "",
    ps: buildRemark(sub)             // "🇯🇵 东京 | 住宅 | 100GB | 到期 06-15"
  });
}
```

> 多个 sub 落在不同线路机 → 不同 host, 客户端列表里是多个节点 (符合预期)。

---

## 7. 续费 / 到期 / 退订

### 7.1 续费 (admin, ACTIVE 期轻量路径)

```
校验 plan.enabled → expires_at = max(expires_at, now) + period_days
→ 派 xray adu 重填 totalBytes = plan.traffic_gb (复用 op 或新增 xray_update_user_quota)
```

> Sprint 1 只做 ACTIVE 期续费 (client 还在)。EXPIRED 期续费 (需重新部署) → Sprint 2 (因为 Sprint 1 到期直接 revoke 了)。

### 7.2 到期 Job (`SubscriptionExpireJob`, 每日 00:30)

```
subs = trade_subscription WHERE status=ACTIVE AND expires_at < now
for each:
  xrayClientService.revoke(sub.xrayClientId)   // op 框架: 远端清理 + 落地机 → COOLING
  sub.status = EXPIRED
```

落地机 revoke 后进 COOLING, 由 `LandingCoolingReleaseJob` (每 30min) 回 AVAILABLE — 这个 Job Sprint 1 要一起做 (否则落地机一直 COOLING 不释放)。

### 7.3 admin 退订

`/admin/sub/cancel`: revoke client + status=CANCELLED (跟到期一样, 只是状态不同 + 即时触发)。

---

## 8. 接口清单汇总

| 模块 | 接口 | 新增/复用 |
|---|---|---|
| 套餐 | `/admin/plan/sku/*` (9 个) | 新增 |
| 订阅 | `/admin/sub/*` (5 个) | 新增 |
| 订阅 URL | `GET /api/sub/{token}` | 新增 |
| 开通/吊销 | `XrayClientService.provision/revoke` | **复用** (op 框架) |
| 落地机占用 | `landingService.occupyById/releaseToCoolingForRevoke` | **复用** |

---

## 9. 实施步骤 (建议顺序, 每步可独立验证)

```
① DDL: trade_plan + trade_plan_resource + trade_subscription (+ Flyway/手动)
② DO + Mapper + Validator (trade_plan 不可变字段校验)
③ 套餐管理 service + controller + admin UI (建 SKU / 绑资源 / 容量) ── 可单独验证
④ allocator (pickFrontline / pickLanding mapper 查询)
⑤ SubscriptionService.adminCreate (allocator + 复用 provision + 建 sub)
⑥ 订阅管理 controller + admin UI (代客下单 dialog / 订阅列表)
⑦ 订阅 URL 端点 (拼 vmess, 线路机域名)
⑧ SubscriptionExpireJob + LandingCoolingReleaseJob
⑨ 续费 + 退订
```

**最小可演示里程碑** (① ~ ⑦): admin 建 SKU → 绑 1 线路机 + 1 落地机 → 代客下单 → 看 xray_client 出来 + 落地机 OCCUPIED + 远端 xray 配置真下发 → 复制订阅 URL → v2rayN 导入连通。

---

## 10. 验证方案

| 步 | 验证 |
|---|---|
| SKU 容量 | 绑 3 落地机 → 容量显示 3; 占用 1 → 剩余 2 |
| 代客下单 | DB: xray_client 新行 + landing.status=OCCUPIED + trade_subscription ACTIVE; SSH 远端 `xray api` 能查到 user/rule/outbound |
| 订阅 URL | curl `/api/sub/{token}` → Base64 解出 vmess → v2rayN 导入 → 真连通 + 出口 IP = 落地机 IP |
| 流量 enforce | 设小 traffic_gb → 跑满 → xray 自动拒连 |
| 到期 | 手动改 expires_at 过去 → 跑 Job → client revoke + landing COOLING → 30min 后 AVAILABLE |
| 并发抢占 | 同 SKU 池只剩 1 落地机, 并发 2 单 → 一成一败 (SKU_OUT_OF_STOCK) |

---

## 11. 决策点 (已定)

1. ✅ **模块**: 新建 `nook-module-trade` (依赖现有 `node-api`; node **暂不拆**; 需补 `nook-module-node-api` 的 `XrayClientProvisionApi` 契约)
2. ✅ **续费范围**: Sprint 1 只做 **ACTIVE 期续费** (延长 expires_at + xray adu 重填 totalBytes)
3. ✅ **14 天 IP 保留**: Sprint 1 **不做** — 到期 Job 直接 revoke client + 落地机 COOLING→AVAILABLE 释放。14 天保留 → Sprint 2
4. ✅ **limit_ip 默认**: **0 不限** (独享 IP 用户独享出口; 防共享后续按 SKU 配)
5. ✅ **sub insert 失败补偿**: **靠对账** — admin 看孤儿 client (无对应 sub) 手动处理; 不做自动 revoke

> 全部决策已定, Sprint 1 可进入编码 (按 §9 实施步骤, 从 ① DDL 起)。
