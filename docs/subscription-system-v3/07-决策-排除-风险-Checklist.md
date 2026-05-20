# 07-决策, 排除项, 风险, 评审 Checklist

> 评审材料汇总 — 不做的项 / 风险表 / 评审打勾 Checklist.

---

## 十二、不在本设计范围 (明确排除)

| 项 | 状态 | 备注 |
|---|---|---|
| 退款 | ❌ | 工单走 admin, 不在系统建模 |
| 用户主动取消订阅 | ❌ | portal 不暴露"取消"按钮; 用户不续费即自然 EXPIRED |
| **Mesh 多备份节点** | ❌ | v3 选择 1 sub 1 server + DNS 切换. 不预部署多 server, 没有客户端 fallback. 故障时 5-15min DNS 缓存恢复 |
| **跨实例配额聚合 enforce** | ❌ | v3 单 server 模式, xray totalBytes 单实例 enforce 准确, 不需要聚合 |
| **sold_traffic_gb / oversubscription_ratio** | ❌ v3 砍掉 | v3 用 SKU 池 IP 数算容量, 不需要 sold/used 双水位 |
| **流量包加购** | ⚠️ Sprint 5+ | 起步不做, 用户流量用完只能买新套餐 |
| 不限流量机房 (UNLIMITED) | ⚠️ Sprint 5+ | 起步 quota_reset_policy 3 种 (CALENDAR_MONTH / BILLING_CYCLE / FIXED) |
| **自动主动迁移** | ⚠️ Sprint 5+ | 起步告警 → admin 手动操作 |
| **复杂评分函数** | ⚠️ Sprint 5+ | 起步用"客户数最少"简单规则 |
| 套餐升降级 (中途换 SKU) | ❌ | 想升级 = 买新套餐 + 旧的不续费 |
| 用户自定义套餐组合 | ❌ | Admin 预定义 SKU + 关联资源池, 用户只能选购 |
| 多区域降级分配 (HK 不够换 SG) | ❌ | 硬拒, 不做 |
| 带宽实时监控 / Mbps 限速 | ❌ | plan_sku.bandwidth_mbps 仅账面展示 |
| 设备数限制 | ❌ | 起步不限设备 |
| 用户账号停用 | ❌ | 用 sa-token 黑名单 |
| auto-renew 自动续费 | ⚠️ Sprint 5+ | 起步无字段 |
| **CDN 套代理 (CF Proxied)** | ❌ | 直连 server IP, 不走 CF 代理. 起步避开 ToS 风险 |
| **Agent 自动升级** | ⚠️ Sprint 5+ | 起步 admin SSH 手动升级 |

---


---

## 十四、风险

| 风险 | 描述 | 缓解 |
|---|---|---|
| 流量 enforce | xray 原生 enforce, 用尽即拒连; 单 server 准确 | ✅ 解决 |
| **故障时 DNS 缓存 5-15min** | server 挂后用户客户端要等 DNS 过期 | 接受 (v3 已知代价); admin 可手动通知活跃用户; Sprint 5+ 看是否升级 mesh |
| **SKU 池配置错误** | admin 给 SKU 关联了错误的 server/IP, 导致分配失败或泄漏 | SKU 编辑页校验: server 必须 LIVE, IP 必须 LIVE+AVAILABLE+ip_type 匹配; 关联表 enabled 字段允许临时禁用 |
| **SKU 池 server 数量不足** | 池里只有 1 个 server, 挂了没备用 | admin UI 警告"该 SKU 池只有 1 个 server, 故障时用户会断网无法切换" |
| **Cloudflare API 不可用** | 新购无法分配子域名 / 切换无法改 DNS | 新购暂停 (写告警); 切换重试机制 |
| **Cloudflare DNS 记录数超限** | 单 zone 上限几千条 (Free) | 监控 DNS 记录数, 接近 80% 上限告警; Pro 计划无上限可升 |
| **Agent 心跳超时误判** | 网络抖动导致心跳没收到 | 3 次累计 (3min) 才判定; 比单次更严格 |
| **Agent 任务执行失败** | xray api 调用失败 / 网络问题 | 任务标 FAILED + retry 3 次; 全失败告警 admin |
| **Agent 升级问题** | 推新版本到所有机器 | 起步 admin SSH 手动升级; Sprint 5+ 加自动升级 |
| 用户买多套餐到期日杂乱 | N 个 sub 各独立到期日 | portal 按到期日升序; 邮件 7/1 天前提醒; 批量续费 |
| 无宽限期, 用户错过续费断网 | 当天到期直接断 | 到期前双次邮件; 14 天 IP 保留期 |
| 多设备无限制被滥用 | 用户分享配置 | 营销定位接受; 极端 admin 重置 UUID |
| admin 误改 SKU 关键字段 | traffic_gb / price_cny 等 | SKU 编辑页关键字段只读, 想改建新 SKU |
| 落地 IP 到期 admin 忘续 | 用户 sub 失联 | 每日告警 + 必填到期日 |
| Stripe webhook 丢失 | 付了钱激活没跑 | 30min 超时释放 + 人工对账 |
| SKU 池售罄 | 商品页"已售罄"用户买不到 | admin 加货告警 + 邮件提示用户"暂时缺货" |
| 节点故障 + 池内无备用 | 单 server 池子的故障 | NODE_FAILOVER ERROR; admin SLA |
| 订阅 URL token 泄露 | 第三方拉用户全部凭据 | 32 char 随机; portal "重置 URL" 一键失效 |
| 用户级 token 泄露影响放大 | 一处泄漏暴露所有 sub | 重置代价小; UA / 频次告警 |

---


---

## 评审 Checklist (v3)

### v3 核心决策

- [ ] **SKU 池模式** (plan_sku_resource 关联表绑定 server + IP 池, 容量数 IP) 最终确认? ⭐⭐⭐
- [ ] **1 sub 1 server + DNS 切换** (放弃 mesh, 接受 5-15min 故障恢复) 最终确认? ⭐⭐⭐
- [ ] **Agent 架构** (主动 push 数据 + pull 任务, backend 不 SSH 拉) 最终确认? ⭐⭐⭐
- [ ] **sub 独享子域名** (每 sub 一个 Cloudflare A 记录, 切换走 DNS) 可接受? ⭐⭐
- [ ] **Cloudflare 集成强依赖** (起步必接 CF API + DNS) 可接受?

### 数据模型

- [ ] resource_server 拆 3 表 (主表 + capacity + **runtime, v3 优化**) 按更新频率分层 可接受? ⭐
- [ ] server / ip_pool 加 lifecycle_state (INSTALLING/READY/LIVE/RETIRED) 可接受?
- [ ] **resource_ip_pool 两层状态** lifecycle (装机) + status (占用, v3 优化) 可接受? ⭐
- [ ] **sub_main 状态双维度** status (业务) + provision_state (流程, v3 优化) 可接受? ⭐
- [ ] **order_item CHECK 约束** type=NEW/RENEW 字段一致性 (v3 优化) 可接受? ⭐
- [ ] **乐观锁 version** 字段加到 sub_main + order_main (v3 优化) 可接受? ⭐
- [ ] 表前缀方案 (resource_/member_/plan_/sub_/order_/alert_/agent_) 可接受?
- [ ] SKU 关键字段不可改 (traffic_gb / period_days / region / ip_type / price_cny 只读, 想改建新 SKU) 可接受?
- [ ] sub_main 不留快照字段 (JOIN plan_sku 实时读) 可接受?
- [ ] 用户级 sub_token (聚合 URL) + sub 级 sub_domain (DNS 入口) 双层结构可接受?

### 流量统计 / 监控 / 告警

- [ ] 流量两层模型 (用户层 xray + 机房层 NIC) 各管各的, 不互相推算 — 可接受?
- [ ] 流量分上下行存, 计费用双向求和, 展示拆明细 — 可接受?
- [ ] quota_reset_policy 3 种 (CALENDAR_MONTH/BILLING_CYCLE/FIXED) — 可接受?
- [ ] 起步告警 5 种 (SKU_OUT_OF_STOCK / SERVER_TRAFFIC_HIGH / RESOURCE_EXPIRING / NODE_FAILOVER / AGENT_OFFLINE) — 可接受?
- [ ] 容量状态机 (NORMAL ↔ THROTTLED, 不加 EXHAUSTED, severity 升级表达紧迫度) — 可接受?

### 业务规则

- [ ] 起步不限设备 + 营销话术调整 — 可接受?
- [ ] SKU 下架 = 老用户禁止续费, 引导买新 SKU — 可接受?
- [ ] 落地 IP 不自动迁移 (admin 手动换 IP [§9.5 04-监控](./04-监控-流量-告警-切换.md)) — 可接受?
- [ ] 无宽限期: 到期立即断, 14 天内续费重新部署 + 保留原 IP — 可接受?
- [ ] 起步不做加购流量包 (Sprint 5+) — 可接受?

### Agent 架构 (见 [05-Agent架构](./05-Agent架构.md))

- [ ] 模板驱动 (backend 渲染 yaml 配置 + push 到 server) — 可接受?
- [ ] collectors + executors 模块化 — 可接受?
- [ ] 任务队列模式 (backend 写任务 + agent 轮询 GET) — 可接受?
- [ ] 轮询 30s 间隔, 配置变更最大延迟 30s-1min — 可接受?
- [ ] Agent 起步阶段手动升级 (SSH 推) — 可接受?

### Sprint 3 接入支付时再决项

- [ ] 三段式锁库存 (reserve / activate / release) 设计可接受?
- [ ] 30min 超时释放 + IP RESERVED 状态机可接受?

### 总体规划

- [ ] Sprint 工期评估 (2.5 + 1.5 + 1 + 1 + Agent 8 周分散) 是否符合预期?
- [ ] 排除项范围是否合适?

---

> 评审通过后将本文档标 ✅ FROZEN, 进入实施.

---

