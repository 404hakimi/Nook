# 09 - 资源模型 v2 + Reconcile 架构 (评审稿)

> **状态: 评审稿, 未实现。** 评审通过后, 本文取代 01 §2.2 / 06 §1.2 / 06 §2 的"落地机池 + 命令式 task"口径。
>
> 本文解决两件事:
> 1. **资源模型收敛** —— 套餐到底怎么对应落地机 / 线路机 (之前 1:N 池 vs 1:1 反复纠结)。
> 2. **开通架构换血** —— 从"后端命令式远端操作"换成"DB 唯一真相 + Agent reconcile 拉平"。

---

## 0. 一句话决策清单 (先对齐, 再看细节)

1. **套餐 = 区域 + 规格, 不绑具体资源**: `region + ip_type + traffic_gb + bandwidth_mbps + period + price`。
2. **落地机 1:1 独占**: 下单时自动匹配**同区域**一台 AVAILABLE 落地机, CAS 占住。
3. **线路机 = 区域池, 自动挑**: 同区域、带宽准入 (`Σ套餐带宽 + 新套餐带宽 ≤ 线路机带宽 × 0.9`), **不超卖**。
4. **强制同区域**: 套餐 / 落地机 / 线路机 region 必须一致; 跨区硬拒 (网络质量)。
5. **DB = 唯一真相**: 后端任何变更**只写 DB (事务内)**, 关键路径不做同步 SSH / 远端调用。
6. **Agent reconcile 拉平远端**: agent 拉"我该有哪些 client" → diff 本地实际 → 自己收敛 → 回报。漂移自愈, 无孤儿。
7. **混合**: 例行 client 生命周期 (开通/吊销/切换) 走 reconcile; 装机 / rotate 这类一次性命令式留 op 框架。

---

## 1. 资源模型

### 1.1 实体关系

```
套餐 trade_plan        ← 纯产品规格 (region + ip_type + traffic + bandwidth + period + price)
   │ 下单支付时 allocator 匹配
   ├─自动占用─→ 落地机 landing   (同区域 AVAILABLE, 1:1 独占, = 卖的独享出口 IP)
   └─自动挑选─→ 线路机 frontline (同区域 LIVE, 区域池, N 客户共享, 带宽准入, 可切换)
        ↓
   xray_client (frontline 上 inbound user + routing → landing socks5 outbound)
        ↓
   trade_subscription (member ↔ plan ↔ xray_client, 周期/状态)
```

### 1.2 `trade_plan` 字段 (region / ip_type 回归)

> 反转说明: 上一版"套餐绑落地机"才把 region/ip_type 改成派生; 现在套餐**不绑、要靠它匹配**落地机, 所以这两个字段**放回套餐自己**, admin 建套餐时选。

| 字段 | 说明 |
|---|---|
| `region_code` | 产品目标区域 (匹配落地机 + 线路机) |
| `ip_type_id` | 产品 IP 类型 (匹配落地机) |
| `traffic_gb` | 每客户月配额 → xray client totalBytes |
| `bandwidth_mbps` | 卖的带宽, **真实生效** (落地机 dante 限速 + 线路机带宽准入), 不再是"仅展示" |
| `period_days` / `price` | 周期 / 售价 (CNY) |
| `enabled` | 上下架 |

- **不再有 `landing_id`, 不再有 `trade_plan_resource`** —— 套餐与具体资源完全解耦。
- 不可变字段 (建后破历史订阅): `region_code` / `ip_type_id` / `traffic_gb` / `period_days` / `price`。

### 1.3 落地机 vs 线路机 (难度不对称)

| 资源 | 关系 | 带宽约束 | 流量约束 | 调度难度 |
|---|---|---|---|---|
| **落地机** | 1:1 独占 (=1 客户) | 静态: 落地机带宽 ≥ 套餐带宽 | 静态: 落地机月配额 ≥ 套餐流量 | 简单 (匹配时查一次) |
| **线路机** | 1:N 共享 | **动态: Σ套餐带宽 ≤ 线路机带宽 × 0.9** | **动态: Σ实际用量可能超线路机月配额** | 难 (唯一要调度的) |

落地机为什么简单: 1 客户独占, 且被 xray totalBytes 卡在套餐流量内 → 出口流量永远 ≤ 套餐流量 ≤ 落地机配额, **永不超额**。
线路机为什么难: 给 N 客户中转, Σ用量可能超月配额, 带宽被并发抢 → **自动挑 + 阈值/故障切换只针对线路机**。

### 1.4 容量计算 (产品页"剩余")

```sql
-- 套餐剩余 = 同区域 + 同 IP 类型 + 规格达标 的 AVAILABLE 落地机数
SELECT COUNT(*) FROM resource_server s
JOIN resource_server_landing l   ON l.server_id = s.id
JOIN resource_server_capacity c  ON c.server_id = s.id
WHERE s.deleted = 0 AND s.server_type = 'landing'
  AND s.lifecycle_state = 'LIVE' AND l.status = 'AVAILABLE'
  AND s.region = :planRegion AND l.ip_type_id = :planIpType
  AND (c.bandwidth_limit_mbps = 0 OR c.bandwidth_limit_mbps >= :planBw)
  AND (c.monthly_traffic_gb  IS NULL OR c.monthly_traffic_gb >= :planTraffic);
```

> 注: 同一批落地机可被同区域多个套餐共享统计 (共享库存), 卖一台两边剩余都 -1, 符合现实。

---

## 2. 约束总表 (谁查 / 何时 / 静态还是动态)

| 约束 | 时机 | 静/动 | 规则 | 失败 |
|---|---|---|---|---|
| 落地机带宽 ≥ 套餐带宽 | 匹配落地机时 | 静态 | `landing.bw ≥ plan.bw` (0=不限跳过) | 该落地机不入候选 |
| 落地机配额 ≥ 套餐流量 | 匹配落地机时 | 静态 | `landing.quota ≥ plan.traffic` (0/null=不限跳过) | 该落地机不入候选 |
| 线路机带宽准入 | 挑机 / 切机 | 动态 | `Σ在挂套餐带宽 + plan.bw ≤ frontline.bw × 0.9` | 跳过该机 |
| 线路机客户数 | 挑机 | 动态 | `活跃客户 < client_max_count` (0=不限) | 跳过该机 |
| 线路机月流量阈值 | 运行期 Job | 动态 | `used ≥ 90% quota` → THROTTLED | 停接新单 + 迁走订阅 |
| 同区域 | 全程 | 硬规则 | plan.region = landing.region = frontline.region | 跨区拒 |

---

## 3. 线路机自动分配 (allocator)

输入: 落地机区域 `R`、套餐带宽 `B`。

```
1. 候选 = 线路机 WHERE region = R AND lifecycle = LIVE
          AND throttle_state = NORMAL AND agent 在线
2. 准入过滤 (在事务内对候选 SELECT ... FOR UPDATE, 防并发超卖):
     committedBw(F) + B ≤ F.bandwidth_limit_mbps × (1 - 预留率)      # 不超卖
     且 activeClients(F) < F.client_max_count                        # 0 = 不限
3. 通过的里挑「剩余带宽最多」的一台 (= F.bw×(1-预留率) − committedBw)
4. 一台都没有 → 告警 NO_FRONTLINE (admin 加线路机/扩容), 下单失败回滚
```

- `committedBw(F)` = 当前路由经过 F 的所有活跃订阅的 `Σ plan.bandwidth_mbps`。
- **主依据 = 剩余带宽** (真实并发瓶颈), 客户数只做二级硬上限, 月流量只做切换触发器 —— 三者各管各的, 不打架。
- **并发安全**: 准入 + 占用在**同一事务**内对所选线路机行加锁, 串行化分配, 杜绝两单同时算通过导致超卖。

### 旋钮 (全局配置)

| 参数 | 默认 | 含义 |
|---|---|---|
| `reserve_ratio` | 0.10 | 线路机带宽留 10% 余量 |
| `contention_ratio` | **1.0 (不超卖)** | 已定: 不超卖。保留参数, 将来想提利用率再调 |
| `traffic_threshold` | 0.90 | 线路机月流量到 90% 触发切换 |

---

## 4. Reconcile 架构 (核心换血)

### 4.1 原则: DB = desired state (期望状态)

```
后端 (admin / portal / Job)
   │ 只写 DB (事务内, 强一致); 关键路径绝不同步 SSH / 远端
   ▼
   DB  ← 唯一真相 (期望状态: 该有哪些 xray_client / 各自规格)
   ▲ 拉期望                            │ 回报实际 + 流量
   │                                   ▼
   Agent (每台机)  ── diff 期望 vs 本地实际 → 自己 adu/rmu/ado 收敛 ──→ 服务器
```

跟 K8s 一个思路: **后端声明"应该是什么", agent 负责"把它变成那样"**。

### 4.2 期望状态契约 (agent 拉什么)

agent 轮询后端接口, 后端按 DB 实时算出**这台机的期望集合**:

- **线路机**: 期望 xray client 列表 = 所有 `status=ACTIVE` 且 `xray_client.server_id = 本机` 的订阅, 每条带
  `{ uuid, email, totalBytes(=plan.traffic_gb), routingRule, outbound→landing socks5, expiry }`。
- **落地机**: 期望 dante 配置 = 当前占用它的订阅的 `{ socks5 认证, bandwidth class(=plan.bandwidth_mbps) }` (1:1, 至多一条)。

agent 把本机实际 xray/dante 配置与期望 diff:
- 期望有、实际无 → 新增 (adu/adr/ado)
- 期望无、实际有 → 删除 (rmu) —— **到期掐断走这条**
- 规格不一致 → 更新 (改 totalBytes / bandwidth)

### 4.3 reconcile 循环

```
agent 每 N 秒:
  desired = GET /agent/reconcile/desired   (后端按 DB 算, 带本机 token)
  actual  = 读本地 xray/dante 实际配置
  for diff(desired, actual): apply
  POST /agent/reconcile/report  (实际状态 + 流量样本)
```

- **幂等**: 反复 reconcile 结果一致。
- **自愈**: 服务器重启 / 漏操作 / 手改乱 → 下次 reconcile 自动修回期望。
- **无孤儿**: 不再有"远端开通成功但 DB 写失败"的不一致 —— 现状文档里"孤儿 client 靠人工对账"这条**直接消失**。

### 4.4 与 op 框架分工 (混合, 落地友好)

| 操作 | 走哪 | 原因 |
|---|---|---|
| client 开通 / 吊销 / 切换 / 改配额 | **reconcile** | 例行、幂等、要自愈 |
| 装机 (xray/dante/agent 安装)、rotate、一次性运维 | **op 框架 (现状)** | 命令式、一次性、需进度 |

### 4.5 轮询周期 + 立即推 (默认值, 可评审改)

| 通道 | 周期 | 说明 |
|---|---|---|
| client 生命周期 reconcile | **5 分钟** (已定) | 到期晚 ≤5min 掐断 (时间到期无所谓); 下单不等这个周期, 走"立即推" |
| 下单支付成功 | **立即推** (已定) | 后端给该 agent 一个轻量"立即 reconcile"信号, 开通近实时 |
| 心跳 / NIC 流量 / xray stats | 1 / 5 min (沿用现状) | 监控类 |

> 代价认知: **最终一致** (分钟级收敛), 下单走立即推即时。换来强一致 DB + 自愈 + 无孤儿, 运营压力大降。

---

## 5. 业务流程 (后端只写 DB, agent 收敛)

### 5.1 下单支付 → 开通

```
[一个 DB 事务]
  1. 占落地机: UPDATE landing SET status='OCCUPIED', occupied_by=member
               WHERE id=? AND status='AVAILABLE'   (CAS, 防双卖)  → 0 行则换下一台
  2. 挑线路机: SELECT ... FOR UPDATE 候选, 准入校验, 选剩余带宽最多
  3. INSERT xray_client (server_id=线路机, ip_id=落地机, uuid, totalBytes=plan.traffic)
  4. INSERT trade_subscription (ACTIVE)
[事务提交] → 给线路机 + 落地机 agent 各发"立即 reconcile"轻推
agent reconcile → 远端开通 → 回报 → 用户连上
```

### 5.2 到期 (你举的例子)

```
SubscriptionExpireJob (或到期判定):
  [DB 事务] sub.status=EXPIRED; 落地机直接释放 OCCUPIED→AVAILABLE (无 14 天保留, 已定)
线路机 agent 下次 reconcile: 期望集合里没这个 client 了 → rmu 掐断
落地机 AVAILABLE → 立即可再卖
```

> 已定: **到期直接释放, 不做 14 天 IP 保留**。可选保留一个极短冷却 (如几分钟) 给 reconcile 收敛, 不给续费保号。

### 5.3 换线路机 (流量阈值 / 故障)

```
触发: 线路机 used ≥90% 月配额 (THROTTLED) 或 心跳 ≥5min 故障
  对该机每个 ACTIVE 订阅:
    [DB 事务] allocator 在同区域池选新线路机 (准入) → UPDATE xray_client.server_id
新线路机 reconcile 加 client; 旧线路机 reconcile 删 client
用户客户端刷订阅 → 拿到新 host → 恢复 (订阅刷新, 不碰 DNS)
```

### 5.4 续费 / 退订

- 续费 (ACTIVE 期): `expires_at += period`; reconcile 更新 totalBytes。
- 退订: `status=CANCELLED` + 落地机释放; reconcile 删 client。

---

## 6. 数据一致性 / 自愈兜底

- **强一致**: 所有写都是 DB 单库事务, 无跨系统两阶段 → DB 永远自洽。
- **兜底 Job**:
  - `SubscriptionExpireJob` / `LandingCoolingReleaseJob` / `FrontlineTrafficSwitchJob` —— 纯 DB 状态推进。
  - 全量 reconcile 是 agent 常态行为, 本身就是对账; 无需额外人工对账。
- **服务端数据有误**: 改回 DB 期望 → 下次 reconcile 自动救活 (正是你要的)。

---

## 7. 相对现状代码的改造范围

**删 (减法)**
- `trade_plan_resource` 表 + 绑定 UI + 容量基于池的算法
- 落地机 allocator (pickLanding 池版) + 同质池区域/IP 校验 (改成匹配时 region/ip_type 过滤)
- (已做的) 套餐成本派生

**改**
- `trade_plan`: 加回 `region_code` / `ip_type_id` (admin 选); `bandwidth_mbps` 必填 + 真实生效
- allocator: 只剩"匹配落地机 (同区域 AVAILABLE 达标) + 挑线路机 (带宽准入)"
- client 生命周期: 从 op-task 命令式 → reconcile 声明式

**加**
- reconcile 端点: `GET /agent/reconcile/desired` + `POST /agent/reconcile/report`
- agent diff/apply 逻辑 (期望 vs 实际)
- "下单立即推" 信号通道
- 限速 enforce: 落地机 dante per-IP (= per-客户) + 线路机 tc 兜底 (文档 06 §6)

---

## 8. 开放问题 (已拍板)

1. ✅ **reconcile 周期**: client 生命周期 **5 分钟** + 下单**立即推**。
2. ✅ **带宽 enforce**: 做 (dante per-IP + tc); 排在核心资源模型 + reconcile 之后 (见 §9 Sprint 1.5)。`bandwidth_mbps` 真实生效。
3. ✅ **14 天 IP 保留期**: **不做, 到期直接释放** (可留极短冷却给 reconcile 收敛)。
4. ✅ **op 框架去留**: **暂留** (装机/rotate 等命令式), client 生命周期转 reconcile 后**逐步剔除**。

---

## 9. Sprint 重排建议 (基于本模型)

| Sprint | 内容 |
|---|---|
| **1 (重做)** | trade_plan (region/ip_type/bandwidth) + 订阅; allocator (匹配落地机 + 线路机带宽准入); **reconcile 开通/到期** (替代 op-task client 路径); 订阅 URL |
| **1.5** | 限速 enforce (dante per-IP + tc) + 链路带宽校验 |
| **2** | 线路机月流量阈值切换 + 故障切换 (都走 DB 改 + reconcile) + 容量看板 + 告警 |
| **3** | 订单 + Stripe 支付 + portal 自助购买/续费 |

---

> 评审重点: §0 决策清单 + §4 reconcile 架构 + §8 开放问题。认可后再把 01/06/08 对齐到本文口径, 然后编码。
