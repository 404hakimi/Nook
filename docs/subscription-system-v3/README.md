# Nook 订阅与套餐系统 v3

> 状态: **设计稿 v3 (SKU 资源池 + Agent 模式)**
>
> **实施进度**: Sprint 0 (基础设施 + Agent 接入) ✅ 全部完成. 下一步: **Sprint 1 — 核心订阅模型 + SKU 池** (`plan_sku` / `plan_sku_resource` / `sub_main` / 单步分配 allocator).
>
> 本目录文档以**现状代码**为基线, 未实施部分按 v3 设计意图保留并标 ❌. 字段层面以 DB schema 为权威 (跟代码 DO 完全对齐), docs 跟代码漂移时改 docs.

---

## 文档结构

| 文件 | 内容 | 主要受众 |
|---|---|---|
| **README.md** (本文件) | 导航 + v3 总览 + 关键决策 | 所有人 |
| [01-架构与产品形态.md](./01-架构与产品形态.md) | 产品形态 + 整体架构图 | 所有人 |
| [02-数据模型.md](./02-数据模型.md) | 表设计 (现状 + 待建) + ER 图 + 设计原则 + 带宽策略 | DBA, 后端 |
| [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) | allocator 算法 + 订阅生命周期 + sub URL + 业务流程 + Job 排期 | 后端 |
| [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) | 流量统计 + 容量状态机 + 告警 + 节点切换 | 后端, 运维 |
| [05-Agent架构与参数化.md](./05-Agent架构与参数化.md) | Go agent 设计 + 任务队列 + 部署 + runtime config 同步 | Agent (Go) 开发, 运维 |
| [07-决策-排除-风险-Checklist.md](./07-决策-排除-风险-Checklist.md) | 排除项 + 风险表 + 历史决策 + 评审 checklist | 评审, 决策 |
| [../dev/backend-coding-standards.md](../dev/backend-coding-standards.md) | Nook 后端开发规范 (强制约束) | 所有后端开发 |

> **历史合并**: 原 08-Agent参数化配置设计 → 已合并到 05; 原 06-Sprint实施计划 → 删除 (按 Sprint 0 已完成, 后续 Sprint 详情按需在 issue tracker 跟踪).

---

## 按角色阅读路径

### 产品 / 决策者

1. README (本文件) — 全局概念
2. [01-架构与产品形态.md](./01-架构与产品形态.md)
3. [07-决策-排除-风险-Checklist.md](./07-决策-排除-风险-Checklist.md)

### 后端开发 (Java)

1. [01-架构与产品形态.md](./01-架构与产品形态.md)
2. [02-数据模型.md](./02-数据模型.md) — 表结构必读 (现状 + 待建均含)
3. [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) — 主战场
4. [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) — 调度任务 / Agent API
5. [05-Agent架构与参数化.md](./05-Agent架构与参数化.md) — backend ↔ agent 协议

### Agent 开发 (Go)

1. [01-架构与产品形态.md](./01-架构与产品形态.md) — 整体
2. [05-Agent架构与参数化.md](./05-Agent架构与参数化.md) — 主要设计
3. [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) — backend 派的任务类型

### 运维 / DevOps

1. [01-架构与产品形态.md](./01-架构与产品形态.md)
2. [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) — 告警 / 故障切换
3. [05-Agent架构与参数化.md](./05-Agent架构与参数化.md) — Agent 部署

---

## v3 关键决策一览

### 核心架构

- ✅ **VPS 实例模式**: 1 套餐 = 1 IP = 1 subscription (不共享池)
- ✅ **SKU 资源池**: `plan_sku_resource` 关联表绑定 SKU ↔ (server + IP 池), 容量 = 数 IP (砍 sold_traffic_gb)
- ✅ **1 sub 1 server + DNS 切换**: 用户独享子域名, 故障 5-15min DNS 缓存恢复
- ✅ **Agent 架构 (Go)**: 服务器跑轻量 agent 主动 push 数据 + pull 任务, backend 不再 SSH 拉
- ✅ **不限带宽**: xray / dante 都不限速, 通过 `xray_node.touchdown_size` 控制单机密度

### 数据模型 (核心变化)

- `resource_server` 拆 6 张表 (主 + credential + billing + dns + capacity + runtime), 按更新频率分层 ✅
- `resource_ip_pool` 双层状态 lifecycle (装机) + status (占用) ✅
- `sub_main` 状态双维度 status (业务) + provision_state (流程) — 待建
- `order_item` CHECK 约束 type=NEW/RENEW 字段一致性 — 待建
- 乐观锁 `version` 字段加到 sub_main + order_main — 待建
- 客户数上限走 `xray_node.touchdown_size` (不在 server 主表) ✅
- 表前缀: `resource_*` / `member_*` / `plan_*` / `sub_*` / `order_*` / `alert_*` / `agent_*` / `xray_*`

### 故障切换

- ✅ Agent 心跳分级判定 (1/3/5min); temp_unhealthy 自动标记
- ❌ Sprint 2 待补: ≥5min 真切换 (`onServerFault`) + `AGENT_OFFLINE` 告警 + 心跳恢复 resolve
- ❌ 池内全 THROTTLED 紧急 fallback (force=true)
- ✅ CF API 调用拆事务外 + ERROR 状态 (避免长事务)

### 排除项 (起步不做)

完整列表见 [07-决策 §1](./07-决策-排除-风险-Checklist.md). 高频项:

- ❌ Mesh 多备份 (Sprint 5+)
- ❌ 跨实例配额聚合 enforce (单 server 准确)
- ❌ 流量包加购 (Sprint 5+)
- ❌ 用户自定义套餐 (admin 预定义)
- ❌ CDN 套代理 (CF Proxied)
- ✅ Agent 自动升级 (已实施, 走 task 派发)

---

## v3 跟 v1 / v2 的演进

| 版本 | 关键特征 |
|---|---|
| v1 | 套餐含 N 个 IP + 共享流量池 (已废弃) |
| v2 | VPS 实例式 + 用户级聚合 sub_token (转向起点) |
| **v3 (当前)** | **SKU 资源池 + 独享子域名 + Agent 架构** |
