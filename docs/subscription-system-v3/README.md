# Nook 订阅与套餐系统设计 v3

> 状态: **设计稿 v3 (SKU 资源池 + Agent 模式)**.
> 实施进度: **Sprint 0 (A-E) ✅ 全部完成** (2026-05-20) — 会员管理 (P1) + 资源管理 v3 改造 (P2) + Agent 接入 (P3) + v3 完整化 (P4) + Agent 管理体验 (P5 含升级 task / 一键清日志 / admin UI / 无日志). 详见 [06-Sprint实施计划 §11](./06-Sprint实施计划.md). 下一步: **Sprint 1 核心订阅模型 + SKU 池**.

---

## 文档结构

本目录按主题拆分, 跨 Sprint 引用清晰. 单文件控制在 200-500 行便于阅读.

| 文件 | 内容 | 主要受众 |
|---|---|---|
| **README.md** (本文件) | 导航 + v3 总览 + 关键决策 | 所有人 |
| [01-架构与产品形态.md](./01-架构与产品形态.md) | 产品形态 + 整体架构图 | 所有人 |
| [02-数据模型.md](./02-数据模型.md) | 表设计 + ER 图 + 带宽策略 | DBA, 后端 |
| [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) | allocator 算法 + 订阅生命周期 + sub URL + 业务流程 | 后端 |
| [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) | 流量统计 + 告警 + 节点切换 + 容量规划 | 后端, 运维 |
| [05-Agent架构.md](./05-Agent架构.md) | Go agent 设计 + 任务队列 + 部署 | Agent (Go) 开发, 运维 |
| [06-Sprint实施计划.md](./06-Sprint实施计划.md) | Sprint 1-5 实施清单 + 依赖前置 | PM, lead |
| [07-决策-排除-风险-Checklist.md](./07-决策-排除-风险-Checklist.md) | 排除项 + 风险 + 评审 Checklist | 评审, 决策 |
| [08-Agent参数化配置设计.md](./08-Agent参数化配置设计.md) | Agent 配置 backend 集中管理 (global default + per-server override + hot reload), 设计稿待评审 | 后端, Agent (Go) 开发 |
| [../dev/backend-coding-standards.md](../dev/backend-coding-standards.md) | Nook 后端开发规范 (强制约束) | 所有后端开发 |

---

## 按角色阅读路径

### 产品 / 决策者
1. README (本文件) — 全局概念
2. [01-架构与产品形态.md](./01-架构与产品形态.md) — 商业模型 + 总览
3. [06-Sprint实施计划.md](./06-Sprint实施计划.md) — 工期规划
4. [07-决策-排除-风险-Checklist.md](./07-决策-排除-风险-Checklist.md) — 评审决策

### 后端开发 (Java)
1. [01-架构与产品形态.md](./01-架构与产品形态.md)
2. [02-数据模型.md](./02-数据模型.md) — 表结构必读
3. [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) — 主战场
4. [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) — 调度任务 / Agent API
5. [05-Agent架构.md](./05-Agent架构.md) — backend ↔ agent 协议

### Agent 开发 (Go)
1. [01-架构与产品形态.md](./01-架构与产品形态.md) — 了解整体
2. [05-Agent架构.md](./05-Agent架构.md) — 主要设计
3. [03-业务核心-算法与流程.md](./03-业务核心-算法与流程.md) — 看 backend 派的任务类型

### 运维 / DevOps
1. [01-架构与产品形态.md](./01-架构与产品形态.md)
2. [04-监控-流量-告警-切换.md](./04-监控-流量-告警-切换.md) — 告警 / 故障切换
3. [05-Agent架构.md](./05-Agent架构.md) — Agent 部署

---

## v3 关键决策一览 (评审重点)

### 核心架构

- ✅ **VPS 实例模式**: 1 套餐 = 1 IP = 1 subscription (不共享池)
- ✅ **SKU 资源池**: `plan_sku_resource` 关联表显式绑定 SKU 到 server + IP 池, 容量 = 数 IP (砍 sold_traffic_gb 复杂度)
- ✅ **1 sub 1 server + DNS 切换**: 用户独享子域名 (Cloudflare DNS), 切换走 DNS 改向 (5-15min 缓存恢复)
- ✅ **Agent 架构 (Go)**: 服务器跑轻量 agent 主动 push 数据 + pull 任务, backend 不再 SSH 拉
- ✅ **不限带宽**: xray / dante 都不限速, 用户跑满 IP 物理上限; 通过 `max_concurrent_clients` 控制单机用户密度间接管控带宽分摊

### 数据模型

- `plan_sku_resource` 关联表 (新增, 核心)
- `sub_main` 加 `sub_domain` + `cf_record_id` (1:1 关系恢复)
- `resource_server` 加 `domain` / `cf_zone_id` / `lifecycle_state` / `max_concurrent_clients` (必填)
- `resource_server_capacity` 拆出, 砍 `sold_traffic_gb` / `oversubscription_ratio`
- `agent_task` 新表 (agent 任务队列)

### 故障切换

- Agent 心跳分级: 1min WARN / 3min 暂时不健康 / 5min 真切换 (避免误判)
- 池内全 THROTTLED 紧急 fallback (force=true) 救急 + ERROR 告警
- CF API 调用拆事务外 + ERROR 状态 (避免长事务 / 孤儿记录)

### 排除项 (起步不做)

- ❌ Mesh 多备份 (Sprint 5+)
- ❌ 跨实例配额聚合 enforce (单 server 准确)
- ❌ 流量包加购 (Sprint 5+)
- ❌ 用户自定义套餐 (admin 预定义)
- ❌ CDN 套代理 (CF Proxied)
- ❌ Agent 自动升级 (Sprint 5+)

---

## v3 跟 v1 / v2 的演进

| 版本 | 关键特征 |
|---|---|
| v1 | 套餐含 N 个 IP + 共享流量池 (已废弃) |
| v2 | VPS 实例式 + 用户级聚合 sub_token (转向起点) |
| **v3 (当前)** | **SKU 资源池 + 独享子域名 + Agent 架构** |

---

## 评审通过后

文档标 `✅ FROZEN`, 开始 Sprint 1 编码.
