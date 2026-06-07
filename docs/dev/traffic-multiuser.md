# 按用户计量 / 共享就绪 改造

> 在 [traffic-model.md](traffic-model.md) 基础上的**升级方案**(评估中,未定):把"落地机 1:1 单计数器"换成"每接入点独立 socks5 端口 + 独立计量",让计量天然按用户。
> **分配先维持 1:1**,将来开共享(一个落地机卖多人)只动分配、计量一行不改。
> 采纳后:本文取代 traffic-model.md 里 `resource_server_traffic.counter_up_bytes/counter_down_bytes`(改为本文的 `resource_socks5_counter`),其余不变。

## 1. 目标与原则

- **现状**:1 落地机 : 1 用户;落地机一个 socks5 计数器 = 那个用户的量(`uk_cert_ip` 1:1)。
- **目标**:计量**按用户(接入点)**,为"超卖 / 共享"就绪。
- **原则**:**计量一次到位**(最难回头改的部分);**分配先 1:1**(易改),将来开共享几乎免费。

## 2. 核心决定:独立计数 + 独立限速落在「端口」

- nft(计数)/ tc(限速)工作在内核**包**层,**看不到 socks5 账号**(应用层),且用户都从线路机来(同源 IP)→ 账号那层拆不开。
- 所以每个接入点(凭证)= 落地机上一个**独立 socks5 端口**:
  - **装一个 dante**,`internal` 监听 **N 个端口**、配 **N 个账号**(不是装多套)。
  - 每端口一个 **nft 计数器**(上下行)+ 一个 **tc class**(限速)。
- 这样按用户**独立计数 + 独立限速**,都落在端口上,沿用 Nook 现成的 nft + tc,可靠、与现状一致。

## 3. 数据流(per-port)

```
落地机 agent
  每接入点: dante 监听独立端口 + 该凭证 auth;每端口 nft 计数器、tc class(限速)
  上报: NIC rx/tx(整机) + biz[{port, up, down}, ...](每端口一条)
        │ 上报
        ▼ node
  resource_server_traffic    只留机器 NIC(rx/tx),不再放 biz
  resource_socks5_counter    新表: 每(落地机, 端口)的 socks5 最新累计(node 测、不认凭证)
        │ trade 按 (落地机, 端口) 读本接入点那条
        ▼ trade
  trade_subscription_traffic 每接入点: 游标 last_counter_* + 用量 up/down/used
                             (源从"读落地机 biz" 改成 "读 resource_socks5_counter 本端口那条")
```

→ 干净副产品:**node 只管机器 NIC,用户 biz 计量全归 trade**(比 1:1 把 biz 塞 node 的服务器表更顺)。

## 4. 表/字段改动(相对 traffic-model.md)

| 表 | 改动 |
|---|---|
| `trade_subscription_certificate` | **+ `socks5_port`**(本接入点在落地机上的端口);`uk_cert_ip` 1:1 约束**先留**(分配不变) |
| `resource_socks5_counter`(**新**, node) | `server_id` · `port` · `up_bytes` · `down_bytes` · `updated_at`(每落地机端口一行;node 写、trade 读;唯一 `(server_id, port)`) |
| `resource_server_traffic` | **去掉** `counter_up_bytes` / `counter_down_bytes`(biz 不再按落地机),只留 NIC + NIC 游标 |
| `trade_subscription_traffic` | 字段不变;计量**源**改成读 `resource_socks5_counter` |
| 额度账本 / 视图 / 物理 NIC | 不动 |

## 5. 各侧改动

- **agent(Go)** 🔴 最重:
  - 落地机一份 dante 监听 **每接入点一个端口** + 该凭证 auth;每端口建 **nft 计数器** + **tc class**(限速)。
  - 上报从单 `bizUsedBytes` → `biz[{port, up, down}, ...]`(每端口一条)。1:1 下就 1 条,结构上 N 就绪。
- **node** 🟠:
  - 对账期望态:从"1 个 socks5"渲染成"**每接入点一个 socks5(端口 + auth + 限速)**"(现在 N=1)。
  - 收 agent 的每端口 biz → 写 `resource_socks5_counter`(按 `server_id, port`,不认凭证)。
- **trade** 🟠:
  - 计量源:由"读落地机 biz"改为"按 `(cert.landing_server_id, cert.socks5_port)` 读 `resource_socks5_counter`"。
  - 其余(差分 / 游标 / 用量 / 扣额度 / 翻篇)**不变**。

## 6. 计量怎么跑(只改一处)

完全沿用 traffic-model.md 的端到端,**唯一区别**:第 3 步"读 node 的 biz 最新值"由
- 旧:读 `resource_server_traffic.counter_up/down`(按落地机)
- 新:读 `resource_socks5_counter`(按 `落地机 + 端口` = 本接入点)

其余(首见记基准、换落地机重打基准、变小=归零、累加 `up/down/used`、按到期扣额度、翻篇封存开新)一字不改。

## 7. 将来真开共享,只需(计量一行不动)

1. 去掉 `trade_subscription_certificate.uk_cert_ip` 1:1。
2. 分配:允许一个落地机挂 **N 个接入点**(超卖比 / 容量)。
3. 端口本就每接入点独立 → nft / tc 天然按用户 → **计量、上报、对账渲染逻辑全不用动**(只是 N 从 1 变多)。

## 8. 备选:一个端口 + dante 内部(对比,不推荐)

| | 多端口(推荐) | 一个端口 + 多账号 |
|---|---|---|
| 计数 | nft 每端口(精确累计) | dante 日志/accounting(解析,较脆) |
| 限速 | tc 每端口(独立) | dante `bandwidth` 子句(按账号,非 tc) |
| 端口 | N 个(后台分配) | 1 个 |
| 代价 | 多管端口 | 放弃 nft+tc,agent 这两块重写 |

→ 多端口保住现成 nft+tc,可靠、改动集中;一个端口省端口但把计数/限速换成 dante 内部,**不推荐**。

## 9. 端口分配

- 后台分配凭证到落地机时,定 `socks5_port`(稳定、对账据此渲染)。
- 每落地机一个端口池(如某段范围),发一个占一个、释放回收。
- 1:1 现状下可先用落地机默认端口起步。

## 10. 改造小步(每步可独立 build / 提交)

1. DDL:`cert` 加 `socks5_port`;建 `resource_socks5_counter`;`resource_server_traffic` 去 biz 两列。
2. agent(Go):每接入点端口 dante + nft + tc;上报 biz map。
3. node:对账渲染每接入点 socks5;biz 写 `resource_socks5_counter`。
4. trade:计量源改读 `resource_socks5_counter`(按落地机+端口)。
5. 联调 1:1(N=1)跑通,与 traffic-model.md 行为一致。
6. (将来)分配 N 容量 + 去 `uk_cert_ip`。

## 11. 代价 / 暂不做

- 比纯 1:1 多约 **40~60%**,堆在 agent(每端口化)、node 对账渲染、新表。换来共享将来几乎免费。
- **暂不做**:N 容量 / 超卖比分配;每用户带宽的公平/突发细化(端口 tc 已能独立限速,公平策略后做);一个端口走 dante 内部那条路。
