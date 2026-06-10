# 流量计量与计费设计

> Nook 流量怎么测、怎么扣套餐、怎么管容量、字段怎么命名的最终设计。本文自包含。
> 相关实体(以代码为准):订阅 `trade_subscription`、接入点/凭证 `trade_subscription_certificate`、服务器 `resource_server`。

## 一、三层心智:测量 / 算钱 / 展示,分开

- **测量**:计数器 → 算增量 → 周期累加。回答"流了多少"。
- **算钱**:配额分配 + 扣减。回答"允许用多少 / 已用多少"。
- **展示**:视图。把上面两层拼给人看。

三层混在一张表 = 难维护。分开后每层各一种表,职责单一。

## 二、两笔流量,互不相干

| | 机器流量 | 用户流量 |
|---|---|---|
| 量什么 | 网卡进站 + 出站 | 用户上行 + 下行(代理实际负载) |
| 粒度 | 每台服务器 | 每个接入点 → 汇总到每个订阅 |
| 用途 | 护住向机房买的流量、到顶限流并切换 | 扣用户套餐、给用户看用量 |
| 字段 | `rx_bytes` / `tx_bytes` | `up_bytes` / `down_bytes` |

> 机器"进出站"和用户"上下行"**不是一个数**:用户下载 1GB,机器要"进一次出一次"≈2GB(中转更多)。机器口径对机房账单,用户口径扣套餐,绝不混。命名上 `rx/tx` 一律指机器、`up/down` 一律指用户。

## 三、字段命名规范(一眼知含义)

- **单位后缀**:字节 `_bytes`、GB `_gb`(人填的大额配额)、速率 `_mbps`。
- **方向**:机器 `rx`/`tx`、用户 `up`/`down`。
- **角色 token**:

| token | 含义 | 例 |
|---|---|---|
| `total_gb` | 总流量配额(服务器/套餐,人填 GB) | `total_gb` |
| `total_bytes` | 每笔额度分配(订阅账本,字节) | `total_bytes` |
| `used_bytes` | 已用/已扣(测量表 = rx+tx 或 up+down;额度表 = 本笔已扣;靠表名定位) | `used_bytes` |
| `counter_<向>_bytes` | 落地机计数器最新值(socks5 源,给 trade 差分) | `counter_up_bytes` / `counter_down_bytes` |
| `last_counter_<向>_bytes` | 游标:上次处理到的计数器值(算增量基准) | `last_counter_rx_bytes` / `last_counter_up_bytes` |
| `bandwidth_mbps` | 带宽速率上限 | `bandwidth_mbps` |
| `remaining_bytes` | 剩余可用 | `remaining_bytes` |

- **周期累计不加前缀**(周期由 `start_time/end_time` 表达)。
> 三个易混的现在分清了:`total_*` = 额度(`total_gb`/`total_bytes`)、`counter_*` = 落地机原始计数器、`last_counter_*` = 游标(上次处理到的计数器值)。

## 四、整条链路(从 agent 到展示)

```
落地机/线路机上的 agent —— 每隔几分钟读一次本机计数器, 直接上报"当前累计值"
   要点:agent **不存任何状态**(服务器被厂商重装就清空了, 存了也没用)
        "上次读到多少 / 已累计多少" 全记在**后端 DB**(状态不在服务器上, 服务器怎么重置都不丢)
   ├─ 机器:rx 累计、tx 累计
   └─ 用户:up 累计、down 累计(防火墙按方向计数,仅落地机)
        │ 上报
        ▼
┌──────────────────────────── node ────────────────────────────┐
│ 额度表 resource_server_quota   (admin 设上限, 不放统计)              │
│ 测量表 resource_server_traffic (每服务器·每周期一行, 当周期那行在写)  │
│   机器: 用 last_counter_rx_bytes/last_counter_tx_bytes 算增量 → 累加 rx_bytes/tx_bytes/used_bytes │
│         used_bytes 到配额 → 置"限流"; 到重置日 → 旧行封存 + 开新行    │
│   用户: 存 counter_up_bytes/counter_down_bytes 最新累计(覆盖, 跨周期不清零)│
└──────────────────────────────────────────────────────────────┘
        │ trade 扫描时, 经接口读该落地机的 counter_up_bytes/counter_down_bytes
        ▼
┌──────────────────────────── trade ───────────────────────────┐
│ 测量表 trade_subscription_traffic (每接入点·每周期一行, 当周期在写)   │
│   游标 last_counter_up_bytes/last_counter_down_bytes: 跟最新值做差 → 本轮增量        │
│   本周期 up_bytes/down_bytes/used_bytes → 给用户看的上下行用量       │
│   到重置日 → 旧行封存 + 开新行(游标带过去) = 自带每周期上下行历史   │
│ 额度表 trade_subscription_quota: 把(上增量+下增量)按到期早的先扣      │
│   剩余 ≤ 0 → 停该订阅名下接入点                                      │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
 视图 v_subscription_usage (每订阅一行, 只读, 不存数)
   = 各接入点本周期用量(traffic 当周期行) 汇总 + 额度(quota) 现拼
        │
        ▼
 前端/后台: 查这一个视图 → 套餐上下行用量 + 总配额 + 剩余
```

## 五、每张表的字段 + 各干什么

> 对称:每个实体都是 `_quota`(允许多少)+ `_traffic`(实际多少)。

### 1. `resource_server_quota` —— 服务器额度/上限(admin 设, 只放上限不放统计)
| 字段 | 作用 |
|---|---|
| `server_id` | 主键, 跟服务器 1:1 |
| `total_gb` | 总流量配额 GB; **照抄厂商面板原值**(单向计费厂商 ×2), 0=不限 |
| `usable_percent` | 月配额实际可用比例(默认 90); 限流阈值 = `total_gb × usable_percent`, 冗余给换机反应延迟 / agent 流量 / 口径误差(见 landing-budget 闸一) |
| `bandwidth_mbps` | 出站带宽上限; 落地机真实限速, 线路机供分配不超卖 |
| `reset_day` | 月度重置日 1–28 |
| `reset_policy` | 重置策略: 按月 / 固定不重置 |
| `created_at` / `updated_at` | 时间 |

### 2. `resource_server_traffic` —— 服务器测量(每服务器·每周期一行)
| 字段 | 作用 |
|---|---|
| `id` | 主键 |
| `server_id` | 所属服务器 |
| `start_time` / `end_time` | 周期起止; `end_time` 空 = 当前在写那行 |
| `rx_bytes` / `tx_bytes` | 本周期进站 / 出站累计(增量累加) |
| `used_bytes` | 本周期机器已用 = rx + tx(对机房配额) |
| `last_counter_rx_bytes` / `last_counter_tx_bytes` | 游标:上次处理到的网卡累计值(翻篇带到新行) |
| `counter_up_bytes` / `counter_down_bytes` | 落地机测到的用户上下行**最新累计值**(覆盖, 跨周期不清零; 给 trade 差分。用户量源头在此) |
| `throttle_state` | 限流态; used 到配额置"限流"; 翻篇清回正常 |
| `last_sampled_at` | 最近上报时刻 |

> 唯一约束 `(server_id, start_time)`;当周期行 = `end_time` 空。**本表周期 = 服务器月度重置日(`reset_day`),与 `trade_subscription_traffic` 的订阅计费周期是两套,各自翻篇,互不影响。**

### 3. `trade_subscription_traffic` —— 用户测量(每接入点·每周期一行; 与 `resource_server_traffic` 对称)
| 字段 | 作用 |
|---|---|
| `id` | 主键 |
| `cert_id` | 接入点(凭证) |
| `subscription_id` | 所属订阅 |
| `landing_server_id` | 在量哪台落地机(换落地机 → 重打游标) |
| `start_time` / `end_time` | 周期起止; `end_time` 空 = 当前在写那行 |
| `up_bytes` / `down_bytes` | 本周期用户上行 / 下行用量 |
| `used_bytes` | 本周期已用 = up + down |
| `last_counter_up_bytes` / `last_counter_down_bytes` | 游标:上次处理到的用户累计值(跟 node 的 `counter_up_bytes/counter_down_bytes` 做差;翻篇带到新行) |
| `last_sampled_at` | 最近计量时刻 |

> 当周期行 = `end_time` 为空那行(每接入点一行);唯一约束 `(cert_id, start_time)`。旧周期行即上下行用量历史。

### 4. `trade_subscription_quota` —— 额度账本(每笔分配一行, 一个订阅可多行)
| 字段 | 作用 |
|---|---|
| `id` | 主键 |
| `subscription_id` | 所属订阅 |
| `quota_type` | 类型: 基础 / 加购 / 赠送 / 补偿 |
| `total_bytes` | 本笔配额 |
| `used_bytes` | 本笔已扣(= 上+下 之和) |
| `start_time` | 发放/生效时间(本笔起效) |
| `end_time` | 到期时间(各笔独立到期) |
| `status` | 状态: 生效 / 用尽 / 过期 / 撤销 |
| `source_ref` | 关联订单(审计, 可空) |
| `created_at` / `updated_at` | 时间 |

> 配额永远用"**多一笔分配**"表达(`quota_type` 区分),剩余 = Σ(生效且未过期)`max(total_bytes − used_bytes, 0)`:
> - **加购流量包**(ADDON):插一行、不动旧的 → 剩余 = 旧 + 新。
> - **重置流量**(周期/付费, BASE):插一笔新 BASE + 旧 BASE 置过期 → 剩余 = 新额度。
> - 两者 `end_time` 都**不超过订阅到期**(发放时按 `min(期望到期, 订阅到期)` 截断;套餐用到一半才买,只在剩余时限内有效)。
> → "重置流量"和"流量包"是**同一机制**,现在不必定死,做哪个都不改表。

### 5. `v_subscription_usage` —— 展示视图(每订阅一行, 只读)
| 字段 | 来源 | 怎么算 |
|---|---|---|
| `subscription_id` | `trade_subscription.id` | 分组键 |
| `up_bytes` | `trade_subscription_traffic.up_bytes` | Σ 该订阅各接入点**当周期行** |
| `down_bytes` | `trade_subscription_traffic.down_bytes` | Σ 当周期行 |
| `used_bytes` | 上两者 | 本周期已用 = up + down |
| `total_bytes` | `trade_subscription_quota.total_bytes` | Σ(生效未过期) |
| `remaining_bytes` | `trade_subscription_quota` | Σ `max(total_bytes − used_bytes, 0)` |

> 视图**不存数**, 每次查现拼; 只聚 trade 自己的表, 不跨 node。(现状:**视图未建**, portal 余量端点由 service 直查 quota 现拼;视图留到 admin 报表需要时再建。)
> ⚠️ `used_bytes`(本周期)与 `remaining_bytes`(全部生效额度)**是两个口径**:无流量包时一致(已用+剩余=配额);有流量包后(包跨周期),本周期已用 + 剩余 ≠ 总配额(包的历史用量不在本周期)。上线流量包前需定义"已用"展示的是本周期还是累计。
> 这是**汇总**视图(每订阅 1 行)。要看**每笔分配明细**(基础 + 各流量包,各自额度/已扣/到期),直接查 `trade_subscription_quota`(它本就是每笔一行),无需另建。

### 6. 顺带对齐(套餐定义)
`trade_plan`:`traffic_gb` → **`total_gb`**(套餐含总流量)、`bandwidth_mbps`(已是)。开通时 `total_gb` 换算成订阅的首笔基础 `total_bytes`。

## 七、计量怎么跑(端到端)

1. **agent**:读本机当前计数器 → **直接上报当前累计值**(不存状态;算增量、抗归零、累加全在后端)。
2. **node 收到**:
   - 机器:`增量 = 本次 − last_counter_rx_bytes/last_counter_tx_bytes` → 累加进当周期 `rx_bytes/tx_bytes/used_bytes`;`used_bytes ≥ total_gb 换算的字节`(留余量见 §九)置限流;到重置日封存旧行、开新行(游标带过去、累加清零)。
   - 用户:把上下行最新累计值**覆盖**进当周期行的 `counter_up_bytes/counter_down_bytes`。
3. **trade 扫描**(每个生效且有落地机的接入点):读 node 的 `counter_up_bytes/counter_down_bytes`。
   - **首次见到该接入点**(`last_counter_up_bytes/last_counter_down_bytes` 为空)或 **它换了落地机**(`landing_server_id` 变了)→ 只把当前值记成游标、**这轮不计增量**(不继承旧机或别人的历史)。
   - 否则 `增量 = 最新 − last_counter_up_bytes/last_counter_down_bytes`(变小=归零 → 按本次重算)→ 累加进 `up_bytes/down_bytes/used_bytes` → (上+下)增量按到期早的先扣进 `quota` → 推进游标。
4. **判停服 / 翻篇**:该订阅 Σ 剩余 ≤ 0 → 名下接入点置"应停";到下个周期 → 发新基础配额 + **各接入点当周期 traffic 行封存(填 `end_time`)、开新行(游标带过去、`up_bytes/down_bytes/used_bytes` 归 0)** → 复活。
5. **展示**:查 `v_subscription_usage`。

## 八、这套设计能解决的场景

| 场景 | 怎么解 |
|---|---|
| 普通用户用流量 | 计量 → 扣配额 → 展示上下行 → 到顶停服 |
| 加购流量包 | `quota` 插一行(类型=加购, 自带配额+到期), 自动并入可用 |
| 付费重置 / 周期重置 | 发一笔新基础配额 + traffic 当周期行封存、开新行; 不改结构 |
| 续费 | 延长订阅到期 + 为新期发基础配额 |
| 服务器重启、计数器归零 | 后端用上次读数做差, 识别"变小=归零"→ 从 0 重算; 累计在后端 DB, 不丢不重 |
| **服务器被厂商重装**(数据全无, 只剩 IP) | 同上: 后端做差识别归零、重新起算。累计量在后端 DB、跟服务器无关, 照样不丢(仅丢重装瞬间未采的尾巴)。前提:重装后 agent 装回同一条 server 记录(同 `server_id`) |
| 上报丢一次 / 重复上报 | 上报是累计值, 后端做差 → 重复或丢一次都不会算错(下次累计值一对比自动补回) |
| 机房账面对不上 | 我方是"自己控制用的保守阈值", `total_gb` 按机房 ~90% 填, 永远先于机房到顶 |
| 线路机流量到顶 | 置限流 + 触发故障切换; 分配时按"剩余流量"挑线路机, 不超卖共享池 |
| 换落地机(故障切换) | `traffic` 换 `landing_server_id` + 重打游标, 不串号、不补漏增量 |
| 落地机换手:A 换 IP/换机, 原机当月给了新用户 B | A 换机→重打游标(量停在换机前);B 接手→首见只记游标, 只算 B 自己的;A/B 互不串。L 这台 `rx/tx` 按机器记 = A+B 之和(对机房账单正确) |
| 查某用户某月用量(含上下行) | 查 `trade_subscription_traffic` 该周期行 + `quota` 该周期基础笔 → 历史自带 |
| 多 IP(以后) | 已是接入点粒度, 每 IP 一行 `traffic`, 能分 IP 展示用量 |
| 用户看上下行 / 运营看机器量 | 两笔流量两套字段, 互不影响 |

## 九、边界与兜底

- **抗归零**:增量 = `本次 ≥ 上次 ? 本次−上次 : 本次`。计数器变小(重启)就按"从 0 重算", 不会负数、不暴增。
- **状态全在后端**:游标 + 累计都存后端 DB, 不在服务器上。服务器重启、被厂商重装都不影响——后端做差识别归零、重新起算即可;只丢"最后一次上报到重置那一刻"的尾巴, 采样勤就小。
- **留余量**:`total_gb` 照抄厂商面板原值(单向厂商 ×2); 限流阈值 = `total_gb × usable_percent`(配额表字段, 管理端默认 90, 现状 100% 触发待改), 冗余把上报尾巴、agent/装机流量、口径差吃掉, 永远先于机房到顶。(定案见 [landing-budget.md](landing-budget.md) 闸一。)
- **不对账机房**:我方数定位是保守阈值, 不追求等于机房账面; 要精确对账须调机房 API(暂不做)。

## 十、为什么这套够"成熟平台"

- **幂等**:上报累计值、后端做差 → 重复上报或丢一次都不算错。
- **重启/重置安全**:游标、累计、配额全在 DB, 不在服务器、不在内存 → 后台服务重启、服务器被重装, 业务逻辑照常、数据不丢。
- **两套计量隔离**:机器流量(按服务器)与用户流量(按订阅)各一套, 落地机换手、用户换 IP 都不串号。
- **可追溯**:服务器每周期一行 `resource_server_traffic`、用户每接入点每周期一行 `trade_subscription_traffic`(含上下行)、每周期一笔基础 `quota` → 历史可查。
- **强一致**:配额按订阅记账(不按机器), 用户换机/换 IP 用量连续不断。

可后做的补强(表已留位, 不阻塞): **对账 job**(周期性核对 Σ`quota.used_bytes` 与 Σ`traffic`当周期, 漂移告警)。用量历史已内建(`traffic` 旧周期行)。

## 十一、落地状态

本文设计已落地运行(表 / 字段 / API / 计量 / 翻篇均按上文定稿), 余两项:

- `v_subscription_usage` 视图未建(见 §五.5 注):portal 余量已由 service 直查 quota 现拼, 视图留到 admin 报表需要时再建。
- 「线路机分配增加剩余流量准入」做了一半:到顶(THROTTLED)已被准入排除, 但选址打分仍只按带宽余量;优先级低(到顶有渐进疏散兜底), 与 [landing-budget.md](landing-budget.md) 闸二同型, 要做时一并。

## 十二、暂不做

- 链路逐层流量分解视图(纯派生, 需要时再加只读装配)。
- 多 IP 的分 IP 展示(表已就绪, 界面后做)。
- 流量包商品目录、分配优先级精细模型。
- 机房 API 精确对账。
