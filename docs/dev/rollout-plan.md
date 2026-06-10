# Nook 链路补强执行计划

> **本文是落地执行的唯一入口**(照着做就能改)。目标态与"为什么这么设计"见三份设计依据: [traffic-model.md](traffic-model.md)(计量计费, 已落地) / [landing-budget.md](landing-budget.md)(落地机预算) / [frontline-failover.md](frontline-failover.md)(线路机候选组)。两边冲突以本文为准。
> 核心判断: **链路骨架已跑通, 本次是"补强"不是"重构"** —— 在四个现成阶段各加一小块增量, 每步独立提交、可验证、坏了退回现状。

---

## 1. 现状基线(先认清已有什么)

一个用户从"买"到"用"的链路**今天就是通的**, 下面是已就绪的骨架(带代码位置):

| 阶段 | 现状已就绪 | 代码位置 |
|---|---|---|
| 配置 | 落地机配额表(total_gb / bandwidth / 重置日)、套餐库存统计 | `ResourceServerQuotaDO`、`ResourceServerAdmission.countCapacityForPlans` |
| 售卖 | 选入口(打分)、选出口(规格达标 + 逐台试占)、签发凭证 | `TradeAllocator.pickFrontline / matchLandings`、`TradeSubscriptionServiceImpl.adminCreate` |
| 下发 | agent 周期拉期望态, 缺补多删收敛(声明式) | `XrayReconcileApiImpl.getDesiredClients`、`nook-agent/internal/reconcile/reconcile.go` |
| 运行 | agent 上报累计计数器, node 差分(机器 rx/tx), trade 差分扣额度, 到顶置限流 | `ResourceServerTrafficServiceImpl`、`TradeTrafficMeteringServiceImpl.meterCert` |
| 切换 | 线路机到顶/掉线疏散(开关默认关) | `FrontlineFailoverJob`、`ResourceServerAdmission.findFrontlinesNeedingFailover` |

**本次要补的缺口**(后面每步对应一个):
- 落地机预算无防线: 阈值 100% 触发无冗余 / 售卖不看本月剩余 / 出口到顶掉线无自动换机。
- 线路机单入口: 一接入点只绑一台, 挂了等刷订阅才恢复, 救不了正在连的会话。

---

## 2. 闭环链路(端到端 + 自愈)

### 端到端主链路(灰 = 现状, `+` = 本次增量)

```
 ① 配置            ② 售卖              ③ 下发对账           ④ 运行计量
 落地机填配额    →  选入口 + 出口    →  agent 拉期望态   →  差分扣额度
 建套餐看库存       下单签发凭证        缺补多删收敛         到顶置限流
 ├ +usable_percent  ├ +剩余预算准入     ├ +tag 编 ipId       └ +阈值 × percent
                    └ +候选组(主+备)    └ +备机下发
 每段失效 = 退回灰框现状, 不卡死链路(声明式对账 + 累计值差分的特性)
 ①④ = 落地机预算线(A); ②③ = 入口/出口共用, 候选组线(B)与预算线(A)都经过
```

### 落地机故障自愈闭环(回应"某环节挂了会不会拖垮链路")

```
   ┌──────────────────────────────────────────────┐
   │                                                │ 回到稳态
 ① 运行态 ──→ ② 检测 ──────→ ③ 换机 ───────┐        │
 用户在用     job 扫到顶/掉线  matchLandings  │        │
 cert 绑      findLandings    setAllocation   │        │
 入口+出口    NeedingFailover  换 ip_id        ↓        │
   ↑                                       ④ 对账自愈   │
   │                                       tag 变       │
   │                                       旧删新建     │
   │                                       agent 零改   │
   │                                          │         │
   └────────── ⑤ 账连续 ←─────────────────────┘         │
              meterCert 见 landing 变 → 重打游标 ────────┘
              不串不漏、不多扣不少扣

 核心不变量: 换的只是出口机器(IP 会变); 计量锚点随 cert 走;
            任一环挂掉 = 暂停在现状, 恢复后下一轮自动收敛。
```

---

## 3. 线 A: 落地机预算(独立成线, 不碰客户端 / 订阅渲染)

### A1 配额加"可用比例", 阈值字段化

- **目标**: 限流阈值从"用满 100%"改为"用满 `total_gb × usable_percent`", 比例按机器可配; 留的冗余吃换机反应延迟 / agent 与装机流量 / 统计口径误差。
- **DDL**: `resource_server_quota` 加 `usable_percent INT NOT NULL DEFAULT 90`(月配额实际可用%), 跟随本表 collation。
- **改**:
  - `ResourceServerQuotaDO` 加 `usablePercent`。
  - `ResourceServerTrafficServiceImpl.markQuotaReached`(:136): 判定改 `used ≥ gbToBytes(totalGb) × usablePercent / 100`(空值兜底 90)。
  - admin 表单: `ResourceServerQuotaUpdateReqVO` / `ServerLandingQuotaUpdateReqVO` / `ServerLandingQuotaRespVO` 加字段 + 落地机管理页输入框(默认 90)。
  - `total_gb` 字段注释改「填厂商面板原值, 单向计费厂商 ×2」。
- **验证**: percent=90 时 used 到 0.9×total 即 THROTTLED; admin 调 percent 下一轮判定即时生效。
- **回退**: 该机 percent 设 100 = 现状 100% 触发, **无需改代码**。
- **失败方向**: 提前限流(保守, 不会反向超支)。

### A2 售卖按"本月剩余预算"准入

- **目标**: 卖套餐 / 算库存时, 剩余预算撑不起一个套餐的机器不卖; 下月预算重置自动恢复可卖。
- **改**:
  - `ResourceServerRules` 加 `hasTrafficBudget(totalGb, usablePercent, currentUsedBytes, planTrafficGb)`: `total_gb × usable_percent 换字节 − 当周期 used ≥ planTrafficGb × 2(口径放大) 换字节`。
  - `ResourceServerAdmission.findMatchingForPlan`(:123 第③步): 把 `meetsPlanSpec` 的**流量项**换成 `hasTrafficBudget`(带宽项保留); 当周期 used 复用 `filterAllocatable` 已查的 trafficMap。
  - `countCapacityForPlans`(:165 第③步): "可售"判定同步加(补查一次当周期 traffic 的 used, 批量一条)。
- **验证**: 建套餐看库存随机器已用上升而下降; 用过半的机器下单分不到, 月初重置后恢复。
- **回退**: 过滤换回 `meetsPlanSpec`。
- **失败方向**: 少卖(保守), 存量订阅零影响。

### A3 出站 tag 编入落地机标识(换机收敛的前置)

- **目标**: 换落地机时, agent 靠 tag 变化自动删旧建新(已核实 agent 对 outbound/rule 是**纯 tag 比对**, 见 reconcile.go)。
- **改**:
  - `XrayConstants.outboundTagOf / ruleTagOf`(:29 / :34): 入参加 ipId, 产出 `out_<certId>_<ipId>` / `rule_<certId>_<ipId>`。
  - `XrayReconcileApiImpl`(:90 / :91): 传 `cert.getIpId()`; **两个 tag 必须同步编入**(rule 内容引用 outbound tag)。
  - agent **零改动**(前缀仍 `out_` / `rule_`, 比对逻辑不变)。
- **验证**: 测试机换一次落地机, 对账日志同轮出现 `-outbound 旧` + `+outbound 新`; 用户连接不中断。
- **上线注意**: 首次发布会全量重建 tag(旧 `out_<cert>` → `out_<cert>_<ip>`), **低峰执行**; 必须在 A4 之前完成。
- **失败方向**: 单接入点对账错, 下一轮自愈(声明式)。

### A4 落地机到顶 / 掉线自动换机

- **目标**: 出口机器用光或宕机, 自动换到有量的新机, IP 变、服务不断、账连续。
- **改**:
  - `ResourceServerAdmission` 加 `findLandingsNeedingFailover()`(镜像 `findFrontlinesNeedingFailover` :229, 落地机版: 查在线落地机 + throttle/offline 判定)。
  - cert 加「按落地机 `ip_id` 查活跃凭证」(service + mapper; 现有 `listActiveByServer` 是按入口 `server_id` 查, 落地机要按 `ip_id`)。
  - 新建 `LandingFailoverJob`(镜像 `FrontlineFailoverJob`; 配置 **enabled 默认 false** + cron; 落地机独占 → **立刻整迁**, 无分批): 每台故障落地机查其活跃凭证 → `matchLandings` 重选(A2 准入自动保证新机有量, 故障机自己被准入排除) → `setAllocation(certId, 原 server_id 不变, 新 ip_id)` → 发 `LANDING` 变更事件。
  - `TradeJobProperties` 加 `landingFailover` 段。
- **验证**: 测试环境压一台到阈值 → job 换机 → agent 收敛新出站 → `meterCert` 见 `landing_server_id` 变重打游标 → 额度扣减连续无跳变; 再演 OFFLINE 路径。
- **回退**: 开关关 = 现状(到顶仅停新分配, 存量留原机)。
- **失败方向**: 开关关 = 不换机; 换机找不到目标 → 留原机 + 告警(同 frontline 兜底)。

**线 A 验收**: 完整演练「卖 → 用 → 到顶 → 自动换机 → 账连续」, 与厂商面板对比超支 ≤ 冗余(默认 10%)。

---

## 4. 线 B: 线路机候选组(每步向后兼容: `standby_server_ids` 空 = 完全现状)

### B1 凭证加候选字段 + 查询口径拆分(纯地基, 零行为变化)

- **DDL**: `trade_subscription_certificate` 加 `standby_server_ids VARCHAR`(有序 CSV, 空 = 无备机), 跟随本表 collation。
- **改**: `TradeSubscriptionCertificateDO` 加字段; 「查某线路机的凭证」按用途拆三口径 —— 对账(主+备) / 容量(只主) / 疏散(主+备); **所有调用方先保持原行为**(读主, 等价现状)。
- **验证**: build + 现有功能回归(字段无人写 = 行为零变化)。
- **失败方向**: 无行为变化。

### B2 对账下发覆盖备机(可单订阅灰度)

- **改**: `SubscriptionCertApi.listActiveByServer`(对账侧)切到"主+备"口径, 备机也会收到该密钥下发。
- **验证**: 手工给一条 cert 填备机 → 备机 agent 把该密钥配上; 清空字段 → 下一轮对账自动删除。
- **回退 / 灰度**: 字段空的订阅 = 现状; 可只给个别订阅填备机做灰度。

### B3 开通选主+备 + failover 组维护

- **改**:
  - `TradeAllocator.pickFrontline` → `pickFrontlines(region, bw, n)`(同一打分取前 N; 区域不足 N 台有几台用几台, ≥1 即可开通)。
  - `adminCreate`(:120)填整组到 `server_id` + `standby_server_ids`。
  - `FrontlineFailoverJob` 从"改单绑定"升级为"组内换血"(坏机移除、幸存顺位、新机补队尾; **开关仍默认 false**); failover 巡检在出现新健康机时把不足 3 台的组自动补满。
- **验证**: 新开通订阅落候选组; 开开关后杀一台线路机 → job 补组 → agent 下发到新机。
- **回退**: 存量无组订阅照旧走单机; 开关关 = 现状。

### B4 渲染 fallback 两层组(客户端线, 另一会话)

- **归属**: 客户端会话。本会话只把数据备好(B1–B3), **契约点 = cert 候选组字段**。
- **内容**: Clash 渲染 `select`(出口手选) × `fallback`(入口自动切)两层; 客户端登录自动加订阅 + 自动更新间隔 5–10 分钟。
- **兼容**: 无组订阅渲染单节点, 天然向后兼容。

**线 B 验收**: 客户端秒切(B4) + 后台分钟级补组(B3) + 刷新级同步(B4)三层联动, 全程落地机计量游标无跳变。

---

## 5. 顺序 / 并行 / 开关一览

| 步 | 依赖 | 默认开关 | 回退方式 | 谁做 |
|---|---|---|---|---|
| A1 | — | 即时生效 | percent 设 100 | 本会话 |
| A2 | A1(读同字段) | 即时生效 | 换回旧条件 | 本会话 |
| A3 | — | 即时生效 | 去 ipId(须 A4 前) | 本会话 |
| A4 | A3 | **enabled=false** | 关开关 | 本会话 |
| B1 | — | 无行为变化 | — | 本会话 |
| B2 | B1 | 字段空=现状 | 清字段 | 本会话 |
| B3 | B1,B2 | **enabled=false** | 关开关 | 本会话 |
| B4 | B1 | 无组=单节点 | — | 客户端会话 |

- 线 A 与线 B **互不依赖, 可并行**。建议起步 **A1 → A2**(最小、独立、立刻有价值), 再 A3 → A4; 线 B 与之并行。
- 业务判定不新增收口位置: 准入 / 规则仍只在 `ResourceServerAdmission` / `ResourceServerRules`; 任务仍是「一类事一个 job + 配置开关」。

---

## 6. 一致性与可用性(为什么这套切分不怕单点故障)

- **最终一致, 无跨服务事务**: 后台只写 DB, 机器配置由 agent 周期对账拉平。任一环(上报 / 任务 / 对账)挂掉 = 暂停在现状, 恢复后下一轮收敛, 没有半成品卡死。
- **上报不准的方向性**: 累计值 + 游标差分, 丢报 / 重复报不算错账; 上报中断 = 少扣用户(伤钱不伤体验) + 心跳告警。
- **库内一致性**: 开通 = 逐台试占 + 唯一键单事务; 换机 = cert 单行 update; 事件 / 变更日志事后发(丢了不影响主流程)。无需新增分布式协调。
- **每步失败模式 = 退回现状**(见上表回退栏): 开关默认关 / 字段为空 / 比例设满, 都等价今天已验证的链路。
