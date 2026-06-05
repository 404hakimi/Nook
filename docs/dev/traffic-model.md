# 流量计量与计费设计

> 本文是 Nook「流量怎么测、怎么计费、怎么管容量、字段怎么命名」的**权威设计**。
> 订阅 / 凭证 / 授予的实体结构见 [subscription-traffic-model.md](subscription-traffic-model.md);
> 两篇若在流量计量部分冲突,**以本文为准**(本文取代那篇里 `member_plan_traffic` 的旧写法)。

## 1. 一句话心智

两套**完全独立**的流量计,绝不能混:

| | 物理流量 (Meter A) | 业务流量 (Meter B) |
|---|---|---|
| 量什么 | 机器网卡 `rx+tx` | 用户 socks5 上下行 |
| 粒度 | per 服务器 | per 订阅(per 接入点) |
| 目的 | 护 IDC 账单 / 触发限流切换 | 算用户套餐 / 给用户看 |
| 到顶 | 限流 + 故障切换 | 停订阅凭证 |
| 归属 | node | trade |

**用户按业务负载 1× 计费**(下 1GB 扣 1GB),展示上下行;中转产生的物理倍数是**运营成本**,摊进定价,**不进用户账单**。

## 2. 中转链路 = 物理倍数(物理改不掉)

链路:`用户 ↔ 线路机 ↔ 落地机 ↔ 公网`(落地机挂在线路机上)。一次**用户负载 1GB**:

| 机器 | 收 rx | 发 tx | 该机物理消耗 |
|---|---|---|---|
| 落地机 | ≈1GB | ≈1GB | **≈2GB** |
| 线路机 | ≈1GB | ≈1GB | **≈2GB** |
| | | **全链** | **≈4GB** |

- 任何转发节点,数据都"进一次 + 出一次" → 双向计费下 **每台 2×**;两跳 = **4×**。这是物理,不是 bug,上线第一天就在发生。
- **计费系数**跟着 IDC 计费方式走:**双向(rx+tx)= 2**、**只算出网(egress,入网免费)= 1**。
- 省钱杠杆:线路机只是中转、**不需要好 IP** → 选"只算出网"的机房当线路机 → 系数 1 → 同样配额承载翻倍。

> 两个"双向"别混:**物理双向** = 同一份数据进网卡+出网卡(会翻倍);**用户双向** = 用户上传+下载两股不同的流(下载场景下行占绝大部分,合计≈下载量,不翻倍)。

## 3. 计费口径(已定)

- **用户**:业务负载(`up + down`)× **1**。100GB 套餐 = 100GB 负载;下载 1GB 扣 1GB。
- **展示**:真实**上行 / 下行** + 套餐已用(= up+down)。绝不拿机器"进站/出站"糊弄用户(进出站每个方向≈混合负载,不等于用户上下行)。
- **成本**:2~4× 物理摊进**定价**。若将来要转嫁给用户(100GB=50GB 可用),只需把"计费系数"调成 2 并在界面**明确标注**,架构不变。

## 4. 容量瓶颈 = 线路机(运营重点)

| | 落地机 | 线路机 |
|---|---|---|
| 采购流量 | 500GB~1TB(充盈) | 1~3TB(紧、贵、加购飙升) |
| ÷系数后可售负载 | 250GB~1TB | 0.5~1.5TB |
| 是否瓶颈 | 否(远大于单套餐 50~100GB) | **是**(被其上所有用户共享) |

- **线路机可售负载 = 线路机流量 ÷ 系数**;2TB 双向线路机 ≈ 1TB 负载,挂 ~10~20 个 50~100GB 用户。
- 落地机:**只计不卡**(充盈)。线路机:**分配按"流量余量"准入**(不只看带宽 Mbps)。
- 安全网已有:线路机 Meter A 到顶 `THROTTLED` + 触发切换。缺的是**分配前就按流量余量挑线路机**。

## 5. 字段命名规范(一眼知是哪层流量)

| 前缀 / 形态 | 含义 | 示例 | 所在表 |
|---|---|---|---|
| `biz_` | **用户业务**(socks5 上下行) | `biz_up_bytes` / `biz_down_bytes` | capacity(源头)、cert_traffic |
| 裸 `rx/tx` | **网卡物理**(机器收发) | `rx_bytes` / `tx_bytes` / `used_traffic_bytes` | capacity |
| `quota_ / used_` | **额度**账本 | `quota_bytes` / `used_bytes` | grant |
| `last_biz_*` | 计量**游标** | `last_biz_up_bytes` | cert_traffic |
| `cycle_biz_*` | **本周期**业务用量 | `cycle_biz_up_bytes` | cert_traffic |

规则:**`biz_` = 用户业务上下行;裸 `rx/tx` = 网卡物理;`quota/used` = 额度。**
物理侧保持 `rx/tx` 不折腾;只把含糊的 `biz_used_bytes`(单值)拆成 `biz_up_bytes` + `biz_down_bytes`。

## 6. 现状的零碎 → 目标

| 现状 | 问题 | 目标 |
|---|---|---|
| `member_plan_traffic.used_bytes` | 与 grant 的 `used_bytes` **重名不同义**;且与 grant 重复记账 | **删表**,已用归 grant |
| `member_plan_traffic.cycle_reset_at` | 与 BASE 授予 `expires_at` 重复 | 周期归 BASE 授予,删 |
| `member_plan_traffic.last_counter_tx` | 单向游标、命名含糊 | 迁入新表,拆 `last_biz_up/down_bytes` |
| `capacity.biz_used_bytes` | 单值、不分向、命名含糊 | 拆 `biz_up_bytes` / `biz_down_bytes` |
| `traffic_gb / used_traffic_bytes / quota_bytes / biz_used_bytes` | 4 个名字指"流量" | 按 §5 规范统一前缀 |

## 7. 目标表结构

物理侧(node)只**一处小改**,其余不动:
```
resource_server_capacity   rx_bytes / tx_bytes / used_traffic_bytes / last_cum_rx_bytes / last_cum_tx_bytes
                           monthly_traffic_gb / throttle_state / period_start / reset_day / quota_reset_policy
                           biz_used_bytes  →  biz_up_bytes + biz_down_bytes        ← 唯一改动
                           (新增 quota_factor: 1=仅出网 / 2=双向, 派生可售负载)
```

业务侧(trade)= 重构重灾区:
```
trade_plan            套餐定义   traffic_quota_gb(=负载配额, total)、bandwidth_mbps、period_days …
trade_traffic_grant   额度账本   quota_bytes、used_bytes(total = up+down, 1× 负载)、grant_type、expires_at、status   ← 不变
trade_cert_traffic    接入点流量  cert_id(uk)、subscription_id、landing_server_id           ← 新, 替 member_plan_traffic
                                 last_biz_up_bytes / last_biz_down_bytes      游标(对 biz 累计计数器做差)
                                 cycle_biz_up_bytes / cycle_biz_down_bytes    本周期业务用量(给用户看)
                                 last_sampled_at
（删 member_plan_traffic）
```

→ **上下行全项目只出现在 2 处**:`capacity.biz_up/down`(源头)、`trade_cert_traffic`(游标+用量)。grant / plan / subscription 一律 total。开发时**只有一处要区分方向**。

## 8. 代码组织(职责切干净)

| 组件 | 唯一职责 | 碰 up/down? |
|---|---|---|
| `BusinessTrafficMeteringService` | 每凭证:读 `biz_up/down` → 算 Δ → 累加 `cycle_biz_up/down` → 把 (Δup+Δdown) 记进 grant | **是(唯一)** |
| `TradeTrafficGrantService` | 额度账本(total):addUsage / remaining / createBase | 否 |
| `TradeLifecycleJob` | 编排:计量 → 判余额 → 停/活 → 翻篇(清 `cycle_biz_*` + 发 BASE) | 否 |
| `TradeAllocator` + `ResourceServerAdmission` | 分配加**线路机流量余量**准入(可售 = (配额−已用)÷系数) | 否 |
| node 物理计量 `ResourceServerTrafficServiceImpl` | NIC rx/tx 累计 + 月度重置 + 限流 | 否(rx/tx) |
| (可选) `CounterDelta` 工具 | "累计值→增量 + 抗回绕",node 的 rx/tx 与 trade 的 biz_up/down 共用,不重复写 | — |

## 9. 计量端到端

1. agent 读落地机 socks5 nft 计数器(**拆两个方向**),上报 `bizUp / bizDown` → node 写 `capacity.biz_up_bytes / biz_down_bytes`(绝对值)。
2. 计量(每个 ACTIVE 且有落地机的凭证):读 `biz_up/down` → 游标算 `Δup / Δdown`(抗回绕、换落地机重打基线)→ `cycle_biz_up += Δup`、`cycle_biz_down += Δdown`、`grant.addUsage(Δup+Δdown)` → 推进 `last_biz_up/down`。
3. 生命周期:订阅剩余(Σ授予)≤0 → 停名下应运行凭证;BASE 授予到期且订阅未过期 → 发下一笔 BASE(翻篇)+ 清该订阅各凭证 `cycle_biz_*` + 原停服的复活。
4. 展示:用户上下行 = Σ其凭证 `cycle_biz_up/down`;剩余/配额走 grant。

## 10. 解释器 / 链路展示(暂不做 — YAGNI)

- **现在没有任何场景需要**。计费、计量、展示上下行均不依赖它。
- 仅当要做前端「**链路流量分解**」视图(用户负载 → 线路机 ×系数 → 落地机 ×系数 → 厂商账单 的逐层展示)时,才需要一个**装配/解释类**把各层数字拼成链路叙事。
- 它是**纯派生**:只要存了原始数(用户 `biz_up/down`、每台 `rx/tx`、每台 `quota_factor`),随时能加,**不改表**。
- 结论:不建。存储保证"可派生"即可,需要时再加一个只读装配器。

## 11. 迁移 / 改造小步(每步 build 绿、可独立提交)

1. `capacity`:`biz_used_bytes` → `biz_up_bytes` + `biz_down_bytes`;加 `quota_factor`。
2. agent(Go):nft 计数器拆双向,上报 `bizUp / bizDown`;`AgentNicTrafficReqVO` 加两字段。
3. 建 `trade_cert_traffic`(DO/Mapper);`trade_plan.traffic_gb` → `traffic_quota_gb`(可选,命名统一)。
4. 计量改 up/down + 按凭证(读 `BusinessTrafficMeteringService`)。
5. 生命周期翻篇:从 BASE 授予到期推导 + 清 `cycle_biz_*`(移出计量)。
6. 展示(getPage/订阅渲染):已用/剩余走 grant,上下行走 `cycle_biz_*`。
7. 分配:`Admission` 加线路机流量余量卡口。
8. 删 `member_plan_traffic`。
9. 重启验证 + 选择性提交(不碰前端 WIP)。

## 12. 暂不做(避免过度设计)

- 链路流量解释器 / 前端链路分解视图(§10)。
- 计费系数转嫁用户(默认 1×;改系数即可,需界面标注)。
- 多 IP 的 per-IP 上下行展示(表已按凭证粒度就绪,UI 后做)。
- 线路机流量"超售"策略的精细模型(先按可售负载硬卡,够用)。
