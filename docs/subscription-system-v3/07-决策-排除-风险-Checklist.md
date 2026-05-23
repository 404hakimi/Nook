# 07-决策, 排除项, 风险

> 评审材料汇总 — 不做的项 / 风险表 / 历史决策记录.

---

## 1. 不在本设计范围 (明确排除)

| 项 | 状态 | 备注 |
|---|---|---|
| 退款 | ❌ | 工单走 admin, 不在系统建模 |
| 用户主动取消订阅 | ❌ | portal 不暴露"取消"按钮; 不续费即自然 EXPIRED |
| **Mesh 多备份节点** | ❌ | v3 选 1 sub 1 server + DNS 切换. 不预部署多 server, 没客户端 fallback; 故障 5-15min DNS 缓存恢复 |
| **跨实例配额聚合 enforce** | ❌ | v3 单 server 模式, xray totalBytes 单实例 enforce 准确 |
| **sold_traffic_gb / oversubscription_ratio** | ❌ v3 砍 | v3 用 SKU 池 IP 数算容量, 不需要 sold/used 双水位 |
| **流量包加购** | ⚠️ Sprint 5+ | 起步不做, 流量用完只能买新套餐 |
| 不限流量机房 (UNLIMITED) | ⚠️ Sprint 5+ | 起步 quota_reset_policy 3 种 (CALENDAR_MONTH / BILLING_CYCLE / FIXED) |
| **自动主动迁移** | ⚠️ Sprint 5+ | 起步告警 → admin 手动操作 |
| **复杂评分函数** | ⚠️ Sprint 5+ | 起步用"客户数最少"简单规则 |
| 套餐升降级 (中途换 SKU) | ❌ | 想升级 = 买新套餐 + 旧的不续费 |
| 用户自定义套餐 | ❌ | admin 预定义 SKU + 关联资源池, 用户只能选购 |
| 多区域降级分配 (HK 不够换 SG) | ❌ | 硬拒, 不做 |
| 带宽实时监控 / Mbps 限速 | ❌ | `bandwidth_mbps` 仅账面展示 |
| 设备数限制 | ❌ | 起步不限设备 |
| 用户账号停用 | ❌ | 用 sa-token 黑名单 |
| auto-renew 自动续费 | ⚠️ Sprint 5+ | 起步无字段 |
| **CDN 套代理 (CF Proxied)** | ❌ | 直连 server IP, 避开 ToS 风险 |
| **Agent 自动升级** | ✅ 已实施 | 走 task 派发 (agent_upgrade), 不再 admin SSH 手动 |

---

## 2. 风险

| 风险 | 描述 | 缓解 |
|---|---|---|
| 流量 enforce | xray 原生 enforce, 用尽即拒连; 单 server 准确 | ✅ 解决 |
| **故障 DNS 缓存 5-15min** | server 挂后用户客户端等 DNS 过期 | 接受 (v3 已知代价); admin 可手动通知活跃用户; Sprint 5+ 看是否升级 mesh |
| **SKU 池配置错误** | admin 给 SKU 关联错 server/IP, 导致分配失败或泄漏 | SKU 编辑页校验: server 必须 LIVE, IP 必须 LIVE+AVAILABLE+ip_type 匹配; 关联表 enabled 字段允许临时禁用 |
| **SKU 池 server 数量不足** | 池里只 1 server, 挂了没备用 | admin UI 警告"该 SKU 池只 1 server, 故障无法切换" |
| **Cloudflare API 不可用** | 新购无法分配子域名 / 切换无法改 DNS | 新购暂停 (写告警); 切换重试机制 |
| **Cloudflare DNS 记录数超限** | 单 zone Free 上限几千条 | 监控记录数, 接近 80% 告警; Pro 计划无上限可升 |
| **Agent 心跳超时误判** | 网络抖动导致心跳没收到 | 3 次累计 (3min) 才判 temp_unhealthy; 比单次更严格 |
| **Agent 任务执行失败** | xray api 调用失败 / 网络问题 | task FAILED + retry 3 次; 全失败告警 admin |
| **Agent 升级问题** | 推新版本到所有机器 | ✅ task 派发 (`agent_upgrade`, sha256 校验) |
| 用户买多套餐到期日杂乱 | N 个 sub 各独立到期 | portal 按到期升序; 邮件 7/1 天前提醒; 批量续费 |
| 无宽限期, 用户错过续费断网 | 当天到期直接断 | 到期前双次邮件; 14 天 IP 保留期 |
| 多设备无限制被滥用 | 用户分享配置 | 营销定位接受; 极端 admin 重置 UUID |
| admin 误改 SKU 关键字段 | traffic_gb / price_cny 等 | SKU 编辑页关键字段只读, 想改建新 SKU |
| 落地 IP 到期 admin 忘续 | 用户 sub 失联 | 每日告警 + 必填到期日 |
| Stripe webhook 丢失 | 付了钱激活没跑 | 30min 超时释放 + 人工对账 |
| SKU 池售罄 | 商品页"已售罄"用户买不到 | admin 加货告警 + 邮件提示用户"暂时缺货" |
| 节点故障 + 池内无备用 | 单 server 池的故障 | NODE_FAILOVER ERROR; admin SLA |
| 订阅 URL token 泄露 | 第三方拉用户全部凭据 | 32 char 随机; portal "重置 URL" 一键失效 |
| 用户级 token 泄露影响放大 | 一处泄漏暴露所有 sub | 重置代价小; UA / 频次告警 |

---

## 3. 历史决策记录

> 设计决策的 trade-off 跟取舍, 给后来者解释"为什么这么做". 越往下越近期.

### 3.1 v3 架构层 (2026-05 初定)

#### 3.1.1 选 1 sub 1 server + DNS 切换, 放弃 mesh 多备份

**决策**: 每个 sub 只部署在 1 个 server 上, 故障靠 DNS 切换恢复.
**代价**: 故障恢复 5-15min (DNS 缓存)
**收益**: 砍掉 mesh 复杂度, 砍掉跨实例配额聚合 enforce, 实施成本 -50%+
**回滚条件**: Sprint 5+ 看运营数据, 若客户对 5-15min 接受度低再升级 mesh.

#### 3.1.2 选 Agent 架构, 放弃 SSH 主动拉

**决策**: 服务器跑 Go agent 主动 push 数据 + pull 任务, backend 不再维护 SSH 连接池.
**代价**: 5-15MB RAM 常驻 + 30s-1min 配置变更延迟
**收益**: 后端横向扩展容易; NAT 后服务器天然支持; 故障检测 1-3min vs SSH 5-10min
**实施**: 已完成 (Sprint 0 P3-P5).

#### 3.1.3 容量计算用 SKU 池数 IP, 砍 sold_traffic_gb

**决策**: SKU 容量 = `COUNT(AVAILABLE IP in pool)`, 不再事前预约 + 用流量 GB 双水位.
**代价**: 容量颗粒度变粗 (一份套餐 = 1 IP)
**收益**: 模型极简, 一句 SQL 就能算; allocator 无需 reserve/release/CAS 容量
**前提**: 已确定 v3 走 VPS 实例模式 (1 sub 1 IP), 容量天然按 IP 数算.

### 3.2 数据模型层

#### 3.2.1 server 拆 6 张表 (主 + credential + billing + dns + capacity + runtime)

**决策**: 按更新频率 + 职责单一原则拆出独立子表 (全部 1:1 PK=server_id).
**为什么必须拆 runtime**:
- 心跳 1min UPDATE, 一台机器一年 50w+ 次
- 心跳写主表会让主表 buffer pool 命中率下降, SELECT (admin 列表) 受影响
- 拆出: 主表"冷数据" 不被高频写锁污染
**为什么拆 credential / billing / dns**: 职责单一 — SSH 凭据 / 账面 / DNS 是三种完全不同的运维域
**实施**: 2026-05 完成全套子表拆分.

#### 3.2.2 删除 `resource_server.max_concurrent_clients` (2026-05-23 本会话决议)

**背景**: 原 v3 设计 `max_concurrent_clients` 字段在 server 主表用作 allocator 硬上限; 实施时发现:
- 实际 allocator 已用 `xray_node.touchdown_size`
- `max_concurrent_clients` 没有任何业务读取, 是死字段
**决策**: 删字段 (`ALTER TABLE resource_server DROP COLUMN max_concurrent_clients`); 客户数上限**只走** `xray_node.touchdown_size`.
**收益**: 跟 [design-simplicity](memory) 偏好一致 — "不要硬上限 + 软上限两层"

#### 3.2.3 MP 全局 update-strategy: NOT_NULL (PATCH 语义)

**决策**: 保留默认, 不改成 IGNORED.
**代价**: update 接口不能把字段从有值清成 null (e.g., billing.expiresAt / dns.domain 没法置空)
**收益**: 凭据密码留空保留原值有自然语义; 多数业务字段也不需要清空
**回滚条件**: 若某 dialog 反复出现"清空操作失效"用户投诉, 局部把对应 DO 字段标 `@TableField(updateStrategy = IGNORED)`.

### 3.3 状态机层

#### 3.3.1 lifecycle 双向流转

**决策**: server / IP 的 lifecycle_state 允许双向 (INSTALLING↔READY, READY↔LIVE, LIVE→RETIRED→LIVE).
**为什么允许 LIVE→READY**: admin 维护场景需要把 LIVE 机器临时下线维护 (不接新订单, 不切走老客户), 修完再 LIVE.
**为什么允许 RETIRED→LIVE**: 误标退役可救回; 也可以"回滚下架"操作.
**实施**: 已落地 (`ALLOWED_LIFECYCLE_TRANSITIONS` Set).

#### 3.3.2 sub_main 双状态机 (business vs provision)

**决策**: `status` (业务: ACTIVE/EXPIRED/CANCELLED) + `provision_state` (流程: PROVISIONED/PROVISIONING/ERROR) 解耦.
**为什么**: 用户付了费但远端 xray 同步失败 (CF 抽风 / agent 离线) 是真实场景, 单一状态字段表达不了"业务可用 + 流程异常". 拆开后:
- 真正可用 = `status='ACTIVE' AND provision_state='PROVISIONED'`
- admin "异常订阅" = `provision_state IN ('PROVISIONING','ERROR')`
**实施**: ❌ 表未建, 等 Sprint 1.

#### 3.3.3 IP 占用状态机 (`AVAILABLE → RESERVED → OCCUPIED → COOLING → AVAILABLE`)

**RESERVED 是 Sprint 3 接 Stripe 时才用** (30min 支付预占). Sprint 1 单步分配跳过 RESERVED 直接 AVAILABLE → OCCUPIED.
**COOLING 防 IP 信誉污染**: 用户退订后 IP 不立刻给下一个用户, 按 `resource_ip_type.cooling_minutes` 冷却 (家宽通常 30+ min).

### 3.4 Agent 层

#### 3.4.1 Agent runtime config 用 task 队列同步, 不开新通道 (2026-05 落地)

**历史**: C-1 ~ C-7 先上了三层 fallback + deep merge + 单调 version 机制, effectiveVersion 公式实施时翻车被砍.
**新决策 (D 系列)**: yaml 整段覆盖 + md5 校验 + 走现有 `agent_task` 通路.
**收益**: 表数量 3 → 1, Java 文件 16 → 6, Go 文件 2 → 1.
**教训**: 个位数 server 的 SaaS 不要上分层 / 合并 / 锁 (记忆 [design-simplicity]).

#### 3.4.2 Agent 任务幂等

**所有 task 设计为可重复执行不出错**:
- `xray_provision_user` 重复 = 已存在 → 不报错
- `xray_remove_user` 重复 = 不存在 → 不报错
- `config_reload` 重复 = md5 一致跳过
- `agent_upgrade` 重复 = 版本一致跳过

允许 backend 失败重试不担心副作用.

---

## 4. 评审 Checklist

### 4.1 v3 核心决策

- [x] **SKU 池模式** (plan_sku_resource 关联表绑定 server + IP 池, 容量数 IP) — 已敲定
- [x] **1 sub 1 server + DNS 切换** (放弃 mesh, 接受 5-15min 故障恢复) — 已敲定
- [x] **Agent 架构** (主动 push 数据 + pull 任务, backend 不 SSH 拉) — 已落地
- [x] **sub 独享子域名** (每 sub 一个 Cloudflare A 记录, 切换走 DNS) — 已敲定
- [x] **Cloudflare 集成强依赖** (起步必接 CF API + DNS) — 已敲定

### 4.2 数据模型

- [x] resource_server 拆 6 表 (主表 + credential + billing + dns + capacity + runtime) — 已落地
- [x] server / ip_pool 加 lifecycle_state (INSTALLING/READY/LIVE/RETIRED) — 已落地
- [x] **resource_ip_pool 两层状态** lifecycle (装机) + status (占用) — 已落地
- [ ] **sub_main 状态双维度** status (业务) + provision_state (流程) — Sprint 1
- [ ] **order_item CHECK 约束** type=NEW/RENEW 字段一致性 — Sprint 3
- [ ] **乐观锁 version** 字段加到 sub_main + order_main — Sprint 1/3
- [x] 表前缀方案 (resource_/member_/plan_/sub_/order_/alert_/agent_/xray_) — 已落地
- [ ] SKU 关键字段不可改 (traffic_gb / period_days / region / ip_type / price_cny 只读) — Sprint 1
- [ ] sub_main 不留快照字段 (JOIN plan_sku 实时读) — Sprint 1
- [x] 用户级 sub_token + sub 级 sub_domain 双层结构 — DDL 已有, 端点待 Sprint 1
- [x] 客户数上限走 `xray_node.touchdown_size` (不在 server 主表) — 已落地

### 4.3 流量统计 / 监控 / 告警

- [x] 流量两层模型 (用户层 xray + 机房层 NIC) 各管各 — 已落地
- [x] 流量分上下行存, 计费用双向求和, 展示拆明细 — 已落地
- [ ] quota_reset_policy 3 种 + 周期切换 Job — Sprint 2
- [ ] 起步告警 5 种 (SKU_OUT_OF_STOCK / SERVER_TRAFFIC_HIGH / RESOURCE_EXPIRING / NODE_FAILOVER / AGENT_OFFLINE) — Sprint 2
- [ ] 容量状态机 (NORMAL ↔ THROTTLED) — Sprint 2

### 4.4 业务规则

- [x] 起步不限设备 + 营销话术调整
- [ ] SKU 下架 = 老用户禁止续费, 引导买新 SKU — Sprint 1
- [x] 落地 IP 不自动迁移 (admin 手动换 IP)
- [x] 无宽限期: 到期立即断, 14 天内续费保留原 IP
- [x] 起步不做加购流量包 (Sprint 5+)

### 4.5 Agent 架构

- [x] 模板驱动 (backend 渲染 yaml 配置 + push 到 server)
- [x] collectors + executors 模块化
- [x] 任务队列模式 (backend 写任务 + agent 轮询 GET)
- [x] 轮询 60s 间隔, 配置变更最大延迟 30s-1min
- [x] Agent 升级走 task 派发 (已实施 agent_upgrade task, 不再手动 SSH)

### 4.6 Sprint 3 接入支付时再决项

- [ ] 三段式锁库存 (reserve / activate / release) 设计可接受?
- [ ] 30min 超时释放 + IP RESERVED 状态机可接受?
