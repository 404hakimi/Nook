# Nook 订阅与套餐系统 v3

> 状态: **资源管理 + Agent 链路 + xray client 运维已落地; 套餐 / 订阅 / 订单 / 计费未开始 (roadmap)**
>
> 本目录文档**以现状代码为基线**: 01-05 描述已实现的部分 (字段层面以 DB schema 为权威, 跟代码 DO 对齐); 所有未实现的 v3 设计意图 (套餐 / 订阅 / 订单 / 告警 / 限速 enforce / 故障切换) 集中在 [06-Roadmap-待建.md](./06-Roadmap-待建.md)。docs 跟代码漂移时改 docs。

---

## 文档结构

| 文件 | 内容 | 现状/路线图 |
|---|---|---|
| **README.md** (本文件) | 导航 + 现状速览 + 关键决策 | — |
| [01-架构与产品形态.md](./01-架构与产品形态.md) | 产品形态 (独享 IP) + 整体架构图 | 现状 |
| [02-数据模型.md](./02-数据模型.md) | **已建表** 完整 DDL + ER + 设计原则 | 现状 |
| [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) | xray client 运维 (op 框架) + 落地机装机 + lifecycle 流转 | 现状 |
| [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) | 流量统计 (xray + NIC) + 心跳监控 | 现状 |
| [05-Agent架构与参数化.md](./05-Agent架构与参数化.md) | Go agent + 任务队列 + 6 种 task + config 同步 | 现状 |
| [06-Roadmap-待建.md](./06-Roadmap-待建.md) | **待建**: 套餐/订阅/订单/告警表 + allocator + 限速 enforce + 故障切换 | 路线图 |
| [07-决策-排除-风险-Checklist.md](./07-决策-排除-风险-Checklist.md) | 历史决策 + 排除项 + 风险表 | 现状 + 决策 |
| [../dev/backend-coding-standards.md](../dev/backend-coding-standards.md) | 后端开发规范 (强制约束) | — |

---

## 现状速览

### 已实现 ✅

| 域 | 能力 |
|---|---|
| **资源管理** | 线路机 (frontline) / 落地机 (landing) 统一为 `resource_server` (server_type 区分) + 6 张子表; CRUD + lifecycle 手动流转 + SSH 流式装机 (xray / dante / agent) |
| **Agent 链路** | Go agent 主动 push (心跳 / NIC 流量 / xray stats) + 拉 task (6 种) + config.yml 整段同步; frontline + landing 双角色 (landing socks5 executor 仍空壳) |
| **xray client** | provision / revoke / rotate / sync / replay — 全走 op 编排框架; 落地机由 admin 手动指定 |
| **危险操作编排** | `OpOrchestrator`: 同 server FIFO 串行 + watchdog 超时 + WebSocket 进度推送 |
| **流量统计** | 用户层 (xray statsquery delta 累加) + 机房层 (vnstat 覆盖写) 双轨 |
| **字典 / 后台** | system_region / system_ip_type 字典; system_user 后台账户 + 审计日志 |

### 待建 ❌ (见 [06-Roadmap](./06-Roadmap-待建.md))

- **套餐**: `trade_plan` + `trade_plan_resource` (SKU ↔ 资源池关联)
- **订阅**: `trade_subscription` + allocator 自动分配 + 订阅 URL (聚合, 线路机域名, 订阅刷新切换)
- **订单**: `trade_order` + `trade_order_item` + Stripe 支付
- **告警**: `alert_record` + 5 种告警 + 容量 throttle 状态机
- **限速 enforce**: 落地机 dante per-IP + 线路机 tc; 字段 / agent executor 全未实现
- **故障切换**: ≥5min 真切换 (`onServerFault`) + 心跳恢复 resolve
- **调度 Job**: 除心跳超时 Job 外全部未建
- **lifecycle 自动推进**: 现状全手动 transition

---

## 关键架构决策

### 核心架构

- ✅ **VPS 实例模式**: 1 套餐 = 1 落地 IP = 1 subscription (不共享池)
- ✅ **落地机 = resource_server**: 落地机不再是独立 IP 池表, 而是 `resource_server` 的 server_type=landing, 跟线路机共用主表 + 通用子表
- ✅ **Agent 架构 (Go)**: 服务器跑轻量 agent 主动 push + pull task, backend 仅装机时 SSH
- ✅ **op 编排框架**: xray client 等危险操作走 OpOrchestrator (FIFO 串行 + watchdog + WS 进度), 不是裸调
- 🟡 **SKU 资源池** (`trade_plan_resource` SKU ↔ server + landing 池, 容量 = 数 landing) — 待建
- 🟡 **1 sub 1 server + 订阅刷新切换** (订阅 URL 动态返回当前节点, 切换改 DB + 客户端刷订阅恢复, 不碰 DNS) — 待建
- 🟡 **dante + tc 双层限速 + 链路守恒** (`Σ落地 ≤ 线路 × 0.9`) — 待建

### 数据模型 (相对旧设计的关键变化)

- 落地机 `resource_ip_pool` → **`resource_server` (server_type=landing) + `resource_server_landing` 子表**
- 字典 `resource_region` / `resource_ip_type` → **`system_region` / `system_ip_type`**
- `resource_server_dns` → **`resource_server_frontline`**
- `xray_node` → 拆 **`xray_server`** (装机事实) + **`xray_config`** (inbound 配置)
- 客户数上限 `xray_node.touchdown_size` → **`resource_server_capacity.client_max_count`**
- `agent_task` 用 **`agent_type` + `source_id`** (非 host_type + host_id); 落地机任务已支持
- `quota_reset_policy` 删 CALENDAR_MONTH, 仅 **BILLING_CYCLE / FIXED**
- 新增 **`op_config` / `op_log`** (危险操作编排) + **`system_user` / `system_user_log`** (后台)

---

## 按角色阅读路径

### 产品 / 决策者
README → [01-架构](./01-架构与产品形态.md) → [06-Roadmap](./06-Roadmap-待建.md) → [07-决策](./07-决策-排除-风险-Checklist.md)

### 后端开发 (Java)
[01-架构](./01-架构与产品形态.md) → [02-数据模型](./02-数据模型.md) → [03-业务核心](./03-业务核心-算法与流程.md) → [04-监控](./04-监控-流量-告警-切换.md) → 要做套餐看 [06-Roadmap](./06-Roadmap-待建.md)

### Agent 开发 (Go)
[01-架构](./01-架构与产品形态.md) → [05-Agent架构](./05-Agent架构与参数化.md) → [03-业务核心](./03-业务核心-算法与流程.md) (backend 派的 task)

---

## v3 跟 v1 / v2 的演进

| 版本 | 关键特征 |
|---|---|
| v1 | 套餐含 N 个 IP + 共享流量池 (已废弃) |
| v2 | VPS 实例式 + 用户级聚合 sub_token (转向起点) |
| **v3 (当前)** | **SKU 资源池 + 订阅刷新切换 + Agent 架构 + 落地机统一为 resource_server** |
