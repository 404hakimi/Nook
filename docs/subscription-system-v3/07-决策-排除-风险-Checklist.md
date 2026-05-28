# 07-决策, 排除项, 风险

> 评审材料汇总 — 不做的项 / 风险表 / 历史决策记录 / checklist。

---

## 1. 不在本设计范围 (明确排除)

| 项 | 状态 | 备注 |
|---|---|---|
| 退款 | ❌ | 工单走 admin, 不在系统建模 |
| 用户主动取消订阅 | ❌ | portal 不暴露"取消"; 不续费即自然 EXPIRED |
| **Mesh 多备份节点** | ❌ | v3 选 1 sub 1 server + 订阅刷新切换; 故障靠客户端刷订阅恢复 |
| **per-sub 子域名 + CF DNS 切换** | ❌ 砍 | 订阅 URL 本身就是间接层, 切换改 DB 即可; 不要订阅数量级 CF 记录 |
| **跨实例配额聚合 enforce** | ❌ | 单 server xray totalBytes 准确 |
| **sold_traffic_gb / oversubscription_ratio** | ❌ v3 砍 | SKU 容量按池内 landing 数算 |
| 流量包加购 | ⚠️ Sprint 5+ | 起步流量用完只能买新套餐 |
| 不限流量机房 (UNLIMITED) | ⚠️ Sprint 5+ | quota_reset_policy 只 2 种 (BILLING_CYCLE / FIXED) |
| 自动主动迁移 | ⚠️ Sprint 5+ | 起步告警 → admin 手动 |
| 复杂评分函数 | ⚠️ Sprint 5+ | 起步"客户数最少"简单规则 |
| 套餐升降级 (中途换 SKU) | ❌ | 买新套餐 + 旧的不续费 |
| 用户自定义套餐 | ❌ | admin 预定义 + 关联资源池 |
| 多区域降级分配 | ❌ | 硬拒 |
| 设备数限制 | ❌ | 起步不限 |
| auto-renew 自动续费 | ⚠️ Sprint 5+ | 起步无字段 |
| **CDN 套代理 (CF Proxied)** | ❌ | 直连 server IP 避开 ToS |
| **Agent 自动升级** | ✅ 已实施 | 走 task 派发 (agent_upgrade) |

---

## 2. 风险

| 风险 | 描述 | 缓解 |
|---|---|---|
| 流量 enforce | xray 原生, 用尽即拒连; 单 server 准确 | ✅ 解决 |
| **带宽限速未实现** | UI 能填 bandwidth_limit_mbps 但 agent 不 enforce | 落地机限速字段 + dante/tc executor 待建 ([06 §6](./06-Roadmap-待建.md)); 当前 UI 字段易误导 |
| **lifecycle 不自动推进** | 线路机 xray 装完停在 INSTALLING, 需手动切 | 自动推进待建; 当前靠 admin 手动 transition |
| **故障恢复非无感** | server 挂后用户要刷订阅才拿到新节点 (非 DNS 自动) | 接受; 客户端导入引导设自动更新订阅间隔 + 切换后邮件/站内信提示"请更新订阅"; Sprint 5+ 看是否升级 mesh |
| **SKU 池配置错误** | admin 关联错 server/landing | SKU 编辑校验: server LIVE, landing LIVE+AVAILABLE+ip_type 匹配 (待建) |
| Agent 心跳超时误判 | 网络抖动 | 3 次累计 (3min) 才判 temp_unhealthy |
| Agent 任务执行失败 | xray api / 网络问题 | task FAILED + retry; 全失败告警 (告警待建) |
| 远端配置漂移 (provision SSH 失败留孤儿) | op 框架 SSH 三段部分成功 | provision 内 saga 反向补偿; revoke 失败留孤儿由 CLIENT_ALL_SYNC 对账修复 |
| admin 误改 SKU 关键字段 | traffic_gb / price_cny | SKU 编辑关键字段只读, 改建新 SKU (待建) |
| 落地 IP 到期 admin 忘续 | 用户失联 | 每日告警 + 必填到期日 (告警待建) |
| 订阅 URL token 泄露 | 第三方拉全部凭据 | 32 char 随机; portal 重置 URL (待建) |

---

## 3. 历史决策记录

> 设计 trade-off, 给后来者解释"为什么这么做"。越往下越近期。

### 3.1 v3 架构层

#### 3.1.1 选 1 sub 1 server + 订阅刷新切换, 放弃 mesh 和 CF DNS 切换
**决策**: 每 sub 只部署 1 个 server; 故障 / 换线路机靠**订阅刷新**恢复 — 订阅 URL (`nook.com/api/sub/{token}`) 动态返回当前节点, 后台切换只改 DB (`xray_client.server_id` 迁到同 region 另一台 frontline), **不碰 DNS**。vmess host 用线路机级固定域名 (装机配 A 记录, 数量 = 线路机数)。
**为什么放弃 per-sub 子域名 + CF DNS** (2026-05 决议): per-sub 子域名 = 订阅数量级的 CF 记录, 管理成本高 + 运行时强依赖 Cloudflare。订阅 URL 本身就是"动态查当前节点"的间接层, 不需要再叠一层 DNS。
**代价**: 故障恢复非无感 — 要客户端刷订阅 (自动更新周期 或 用户手动) 才拿新节点, 不像 DNS 几分钟自动恢复。
**收益**: 砍 mesh + 跨实例配额聚合 + CF per-sub DNS; 后台完全掌控切换, 无外部依赖。
**回滚条件**: Sprint 5+ 若客户对"刷订阅才恢复"接受度低, 再考虑 mesh 或 DNS 切换。

#### 3.1.2 选 Agent 架构, 放弃 SSH 主动拉
**决策**: server 跑 Go agent 主动 push + pull task。
**收益**: 后端横向扩展; NAT 天然支持; 故障检测 1-3min。 **实施**: ✅ 完成。
**保留**: 但 xray client provision/revoke 仍走 op 框架 + SSH CLI (运维操作要实时 + 进度), 心跳/流量/配置走 agent。两通路并存。

#### 3.1.3 容量用 SKU 池数 landing, 砍 sold_traffic_gb
**决策**: SKU 容量 = `COUNT(AVAILABLE landing in pool)`。
**收益**: 模型极简, 一句 SQL; allocator 无需 reserve/release 容量。

#### 3.1.4 **落地机统一为 resource_server (重大演进)**
**背景**: 旧 v3 设计落地机是独立 `resource_ip_pool` 表 (一 IP 一行)。
**决策**: 落地机改为 `resource_server` 的一行 (`server_type='landing'`), 跟线路机共用主表 + credential/billing/capacity/runtime 子表, 各挂角色专属子表 (frontline → `resource_server_frontline` DNS, landing → `resource_server_landing` socks5/dante/占用)。
**收益**:
- 线路机 / 落地机的 SSH 装机 / agent / 心跳 / 流量监控 / systemd 运维**完全复用同一套代码** (装机流程、详情页 tab、agent 链路都共享)
- 避免 ip_pool 跟 server 两套并行的 CRUD / 凭据 / 监控逻辑
**代价**: `resource_server_landing` 比纯 IP 池表多挂在 server 上; xray_client.ip_id 指向 resource_server.id (landing)。
**实施**: ✅ 已落地 (2026-05)。

#### 3.1.5 **xray client 共享 inbound + per-client 路由 (三段式)**
**决策**: 不是"每客户一个 inbound", 而是一个共享 inbound (vmess+ws) + 每客户 (user + routing rule + socks outbound)。
**为什么**: 共享入口省端口 / 省 TLS 证书; 按 email 路由到各自落地出口实现"1 客户独享 1 落地 IP"。
**实施**: ✅ (`ClientOpExecutor` + XrayInbound/Routing/Outbound CLI)。

### 3.2 数据模型层

#### 3.2.1 server 拆主表 + 6 张子表
**决策**: 按更新频率 + 职责单一拆 (credential/billing/frontline/landing/capacity/runtime, 全 1:1 PK=server_id)。
**为什么拆 runtime**: 心跳 1min UPDATE, 一年 50w+ 次, 拆出避免污染主表 buffer pool。
**实施**: ✅。

#### 3.2.2 **xray_node 拆 xray_server + xray_config**
**决策**: 旧单表 `xray_node` 拆成 `xray_server` (装机事实: 版本/路径/systemd, 低频) + `xray_config` (inbound 配置: 协议/传输/TLS, 业务可热改)。
**为什么**: 装机事实装完一般不动, inbound 配置业务热改, 更新频率 + 职责不同。
**实施**: ✅。

#### 3.2.3 **客户数上限 touchdown_size → capacity.client_max_count**
**背景**: 旧设计客户数硬上限字段 `xray_node.touchdown_size`。
**决策**: 改名 `client_max_count` 并挪到 `resource_server_capacity` (容量域)。provision 时校验, xray reinstall 不能缩到比当前活客户数小。
**实施**: ✅。

#### 3.2.4 **字典归 system 模块 (resource_* → system_*)**
**决策**: `resource_region` / `resource_ip_type` → `system_region` / `system_ip_type` (字典属系统域, 非资源域)。
**实施**: ✅。

#### 3.2.5 **quota_reset_policy 删 CALENDAR_MONTH**
**背景**: 旧设计 3 种 (CALENDAR_MONTH / BILLING_CYCLE / FIXED)。
**决策**: 删 CALENDAR_MONTH, 只留 BILLING_CYCLE (按账单日) / FIXED (永不重置, 默认)。自然月重置由账单日覆盖, 不单列。
**实施**: ✅ (`ResourceServerQuotaResetPolicyEnum`)。

#### 3.2.6 限速方案: dante 落地机级 + tc 线路机级兜底 (待建)
**决策**: 落地机 dante per-IP 限速 + 线路机 tc 出站接口级兜底 + 链路守恒 (`Σ落地 ≤ 线路 × 0.9`, 预留率 10%)。
**对比**: xray 客户级限速 ❌ (原生不支持 per-user bandwidth); 单 dante 无兜底; 单 tc 颗粒太粗。
**前提**: 落地机已跑 agent (✅); 但**落地机 bandwidth_limit_mbps 字段未加 + agent 限速 executor 未实现**。
**实施进度**: ❌ 全部待做, 见 [06-Roadmap §6](./06-Roadmap-待建.md)。

#### 3.2.7 MP 全局 update-strategy: NOT_NULL (PATCH 语义)
**决策**: 保留默认。 **代价**: update 不能把字段从有值清成 null。
**收益**: 密码留空保留原值有自然语义。 **回滚**: 个别字段标 `@TableField(updateStrategy = IGNORED)`。

### 3.3 编排 / 状态机层

#### 3.3.1 **危险操作走 OpOrchestrator 编排框架**
**决策**: xray client provision/revoke/rotate/sync + xray restart/autostart 走 `OpOrchestrator` (同 server FIFO 串行 + watchdog 超时 + WebSocket 进度 + op_log 流水), 不裸调 service。
**为什么**: 同 server 并发 xray API 调用会互相踩 (e.g., 同时 provision 两个客户改同一 inbound); 危险操作要有进度可视 + 去重 + 超时保护。
**实施**: ✅ (op_config / op_log + DefaultOpOrchestrator + 8 个 OpHandler)。

#### 3.3.2 lifecycle 双向流转
**决策**: lifecycle_state 允许双向 (INSTALLING↔READY, READY↔LIVE, LIVE→RETIRED→LIVE)。
**为什么 LIVE→READY**: 维护场景临时下线; **为什么 RETIRED→LIVE**: 误标可救回。
**实施**: ✅ (`ALLOWED_LIFECYCLE_TRANSITIONS`)。
**未做**: 自动推进 (装机完成自动 →READY/LIVE), 现状全手动, 见 [06-Roadmap §11](./06-Roadmap-待建.md)。

#### 3.3.3 落地机占用状态机 (`AVAILABLE → RESERVED → OCCUPIED → COOLING → AVAILABLE`)
**RESERVED** Sprint 3 接 Stripe 才用 (30min 支付预占); 当前 provision 直接 AVAILABLE → OCCUPIED。
**COOLING** 防 IP 信誉污染: revoke 后按 `system_ip_type.cooling_minutes` 冷却。
**实施**: ✅ (`ResourceServerLandingStatusEnum` + occupyById / releaseToCoolingForRevoke)。

#### 3.3.4 trade_subscription 双状态机 (待建)
**决策**: `status` (业务) + `provision_state` (流程) 解耦, 真正可用 = ACTIVE + PROVISIONED。
**实施**: ❌ 表未建, Sprint 1。

### 3.4 Agent 层

#### 3.4.1 Agent runtime config 用 task 队列同步, 不开新通道
**历史**: 先上三层 fallback + deep merge + version 机制, effectiveVersion 公式翻车被砍。
**新决策**: yaml 整段覆盖 + md5 校验 + 走现有 agent_task。
**收益**: 表 3→1, Java 16→6 文件。 **教训**: 个位数 server 别上分层 / 合并 / 锁。

#### 3.4.2 agent_task 用 agent_type + source_id
**决策**: 任务表用 `agent_type` (frontline/landing) + `source_id` (= resource_server.id), 不是旧设计臆想的 host_type + host_id。落地机统一为 resource_server 后, source_id 直接是 server.id。
**实施**: ✅。

#### 3.4.3 Agent 任务幂等
所有 task 可重复执行: provision 重复=已存在不报错, remove 重复=不存在不报错, config_reload md5 一致跳过, upgrade 版本一致跳过。

---

## 4. 评审 Checklist

### 4.1 v3 核心决策
- [x] **落地机统一为 resource_server** (server_type=landing) — 已落地
- [x] **Agent 架构** (push 数据 + pull task) — 已落地
- [x] **op 编排框架** (危险操作 FIFO 串行) — 已落地
- [x] **xray 共享 inbound + per-client 路由** — 已落地
- [ ] **SKU 池模式** (trade_plan_resource) — Sprint 1
- [x] **1 sub 1 server + 订阅刷新切换** (放弃 mesh / CF DNS) — 设计敲定
- [x] **订阅地址间接层** (member sub_token + 线路机级域名, 无 per-sub 子域名) — 设计敲定

### 4.2 数据模型
- [x] resource_server 拆主表 + 6 子表 — 已落地
- [x] server / landing lifecycle_state (INSTALLING/READY/LIVE/RETIRED) — 已落地
- [x] 落地机两层状态 lifecycle + status (占用) — 已落地
- [x] xray_node 拆 xray_server + xray_config — 已落地
- [x] client_max_count 在 capacity (非 xray) — 已落地
- [x] 字典 system_* 化 — 已落地
- [x] quota_reset_policy 只 BILLING_CYCLE / FIXED — 已落地
- [x] op_config / op_log — 已落地
- [ ] trade_subscription 双状态机 + 乐观锁 version — Sprint 1
- [ ] trade_order_item CHECK 约束 — Sprint 3
- [ ] SKU 关键字段不可改 — Sprint 1
- [x] member sub_token 聚合订阅 (无 per-sub sub_domain) — sub_token DDL 已有, 端点待建

### 4.3 流量 / 监控 / 告警
- [x] 流量两层 (用户层 xray + 机房层 NIC) — 已落地
- [x] 流量分上下行存, 计费用双向求和 — 已落地
- [x] 心跳分级 + temp_unhealthy 自动标 — 已落地
- [ ] quota_reset 周期切换 Job — Sprint 2
- [ ] 告警 5 种 (alert_record) — Sprint 2
- [ ] throttle 状态机 (NORMAL↔THROTTLED) — Sprint 2
- [ ] 故障切换 (onServerFault) — Sprint 2
- [ ] 限速 enforce (dante / tc) — Sprint 1.5

### 4.4 业务规则
- [x] 起步不限设备
- [x] 落地 IP 不自动迁移 (admin 手动换)
- [x] 无宽限期: 到期立即断, 14 天保留原 IP (设计)
- [ ] SKU 下架 = 禁止续费引导买新 — Sprint 1

### 4.5 Agent 架构
- [x] 模板驱动 (backend 渲染 yaml)
- [x] collectors + executors 模块化
- [x] 任务队列 (backend 写 + agent 轮询 60s)
- [x] Agent 升级走 task (agent_upgrade)
- [ ] 落地机 socks5 executor (当前空壳) — Sprint 1.5

### 4.6 Sprint 3 接支付时再决
- [ ] 三段式锁库存 (reserve / activate / release)
- [ ] 30min 超时释放 + landing RESERVED 状态机
