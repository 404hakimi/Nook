# 订阅 / 凭证 / 流量 数据模型设计

> 适用范围:用户购买 → 资源分配 → 接入凭证 → 流量计量 这条链。
> 目标读者:后端维护者。这份文档是该业务的「权威心智模型」,改这块前先读它。

## 1. 核心心智(先记这条)

- **后台只管业务**:卖套餐、收订阅、分配资源(挑线路机 / 落地机)、签发凭证、管流量额度。
- **agent 只负责落实**:读后台给的「期望态」(凭证:身份 + 密钥 + 该跑在哪),在服务器上把 xray 客户端建出来 / 收敛掉。
- 所以数据库存的是**期望态**,不是"xray 客户端"本身;真正的 xray 客户端是 agent 落出来的运行态。
- **对账 / 同步成功与否**是运维可观测性,不是用户维度业务数据 → 以后单独做一张**全局对账表**,不进每个用户的行。
- 数据量不大,**业务读取靠数据库视图聚合**;写入仍走代码(职责清晰)。

## 2. 设计目标

- **一个订阅可对应 N 个接入点(IP)**,现在 N=1,以后加购不改表。
- **流量可扩展**:基础额度 + 加购流量包 + 活动赠送 + 故障补偿,统一一种机制。
- **表一次设计到位**,后期加能力只插数据,不改结构。
- **现阶段只迁结构、保持现有行为**,加购 / 多 IP / 多流量包逻辑后续再补。

## 3. 统一术语(Ubiquitous Language)

| 术语 | 含义 | 归属 |
|---|---|---|
| 套餐 plan | 商品定义(基础流量、周期、价格、区域/IP 类型) | trade |
| 订阅 subscription | 一次购买 = 商业单位(状态、到期、计费周期) | trade |
| **订阅凭证 certificate** | 订阅名下**每个接入点的期望态**:身份 + 密钥 + 分配到的线路机/落地机 + 期望状态。后台签发与分配,agent 据此落实 | trade |
| **流量授予 grant** | 一笔流量额度,有类型 + 到期(基础 / 加购 / 赠送 / 补偿) | trade |

### 两种「流量额度」是两回事(最容易混,务必分清)

| | 服务器月总流量 | 用户套餐流量 |
|---|---|---|
| 粒度 | per 服务器 | per 订阅 |
| 目的 | 保护机器 / IDC 带宽账单 | 用户买了多少能用多少 |
| 存储 | `resource_server_capacity`(rx/tx/used/限流) | `trade_traffic_grant` 之和 |
| 到顶后 | 限流该服务器 → 触发故障切换 | 暂停该订阅(停名下所有接入点) |

**流量包加的是右边(用户额度),与左边服务器物理配额无关,互不影响。**

## 4. 模型总览

```
member_user (system)
   └─1:N─ trade_subscription                订阅 = 商业单位
             ├─1:N─ trade_subscription_certificate   订阅凭证 = 每接入点的期望态(身份+密钥+分配+期望状态)
             └─1:N─ trade_traffic_grant              流量授予(基础/加购/赠送/补偿)

resource_server_capacity (node)            服务器月总流量 —— 独立, 与上方无关
xray_server / xray_config (node)           线路机上的真·xray 实例与共享配置
(future) 全局对账表                          agent 落实结果 / 漂移记录, 系统维度, 非用户维度
```

关系:
- 订阅 1:N 凭证(现在 N=1)。
- 订阅 1:N 流量授予。
- 凭证的业务流量从 `resource_server_capacity.biz_used_bytes`(按落地机 `ip_id`)读,**不重复存**。

## 5. 目标表结构

> 主键 `char(32)`,实体继承 `BaseEntity`(id / created_at / updated_at 不在 DO 重复声明)。DDL 不带 FOREIGN KEY、不带 DEFAULT(默认值由 Service 显式赋)。

### 5.1 trade_subscription(改:去掉 xray_client_id)
保留商业字段(member_user_id / plan_id / started_at / expires_at / status),**删除** `xray_client_id` 列及唯一索引 `uk_sub_xray_client`(关系反转到凭证侧)。流量不在订阅上存,由 grant 之和表达。

### 5.2 trade_subscription_certificate(新;原 xray_client 的业务部分搬来,合二为一)
```
id              char(32)      主键
subscription_id char(32)      所属订阅
source          varchar(16)   来源: BASE=基础 ADDON=加购
server_id       char(32)      分配的线路机(故障切换只改这个字段)
ip_id           char(32)      分配的落地机
auth_user       varchar(128)  连接身份(agent 按协议渲染成 xray email 等)
auth_secret     varchar(64)   连接密钥(agent 按协议渲染成 uuid / password)
cert_status     varchar(16)   期望态: ACTIVE=应运行 SUSPENDED=应停 REVOKED=应移除
created_at / updated_at
unique uk_cert_ip (ip_id)                         落地机与接入点 1:1
index  idx_cert_subscription (subscription_id)
index  idx_cert_server (server_id, cert_status)   agent 按线路机拉应运行的接入点
```
- `cert_status` 是**期望态**:后台决定它该不该跑(到期/欠费置 SUSPENDED、退订置 REVOKED),agent 照着收敛。**不存"实际同步成功没"**——那是对账态,见 §8。
- `auth_user/auth_secret` 协议中立;渲染成 vmess/vless 的 uuid 还是 trojan 的 password,由 node 协议映射决定。轮换 = 重置 `auth_secret`。
- 释放落地机时 `ip_id` 置空再分配(MySQL 唯一索引允许多空值)。

### 5.3 trade_traffic_grant(新)
```
id              char(32)      主键
subscription_id char(32)      所属订阅
grant_type      varchar(16)   BASE/ADDON/PROMO/COMPENSATION  基础/加购/赠送/补偿
quota_bytes     bigint        本笔额度(字节)
used_bytes      bigint        本笔已消耗(字节)
granted_at      datetime      发放时间
expires_at      datetime      到期时间
status          varchar(16)   ACTIVE/USED_UP/EXPIRED/REVOKED  生效/已用尽/已过期/已撤销
source_ref      char(32)      关联订单(审计, 可空)
index idx_grant_sub (subscription_id, status, expires_at)
```
**订阅有效剩余额度 = 所有「生效中且未过期」grant 的 `quota_bytes - used_bytes` 之和。** 基础额度也是一条 `grant_type=BASE`、到期=账单周期的 grant。

### 5.4 resource_server_capacity(不变)
服务器月总流量(rx/tx/used/月配额/限流态)继续在此,与订阅流量两套口径。

## 6. 业务读取:用视图聚合

数据量小,"看一个用户的全貌""管理后台列表"这类**读**,用数据库视图把 订阅 + 凭证 + grant 汇总 + capacity 业务流量 join 起来,直接查视图,少写 Java 聚合代码。

约束:
- **视图只用于读**;所有写仍走代码(开通/分配/吊销/计量),职责清晰。
- **agent 对账热路径不要走视图**——那是精确查询(按 server_id + cert_status),走专门 Mapper。

(具体视图 SQL 在接入实现阶段再定,字段定稿后建。)

## 7. 模块归属(一个要拍的点)

- **trade 拥有** `trade_subscription` / `trade_subscription_certificate` / `trade_traffic_grant`。
- **node 拥有** `resource_server*` / `xray_server` / `xray_config` 及 agent / SSH 基建。
- 凭证里的 `server_id` / `ip_id` 是**资源分配结果**,由 node 侧的分配/故障切换逻辑产生 → 推荐:**trade 拥有凭证表,node 经一个小 api 写分配**(设置/清空 `server_id`、`ip_id`;以及"按 server 列应运行的凭证"供 agent 拉)。不直连对方 Mapper。

## 8. 对账 / 同步态:后期做全局表(本期不做)

- 本期凭证只存**期望态**(`cert_status` + 分配);agent 负责让现实收敛到期望态。
- "实际是否同步成功、有没有漂移"是系统级可观测性 → **以后单独建一张全局对账表/日志**(系统维度,不进用户行)。
- 代价:本期暂时不在库里跟踪"某用户接入点是否已同步"。可接受(开发期),需要观测时再补全局对账表。

## 9. 场景走查

| 场景 | 落地 |
|---|---|
| 普通买套餐(1 IP) | 1 订阅 + 1 凭证 + 1 条 BASE grant |
| 套餐带 2 IP | 1 订阅 + 2 凭证 + 1 条 BASE grant |
| 后期加购 IP | 订阅下加 1 条凭证(source=ADDON),共享订阅到期 |
| 加购流量包 | 插 1 条 grant(type=ADDON,带自己到期) |
| 活动赠送 / 故障补偿 | 插 1 条 grant(type=PROMO / COMPENSATION),不经支付 |
| 流量到顶 | 所有生效 grant 余额=0 → 订阅名下所有凭证 `cert_status=SUSPENDED` |
| 月度重置 | 账单日:重新发放 BASE grant;加购/赠送 grant 按各自到期独立失效 |
| 到期 / 退订 | 订阅置过期 → 名下凭证 `cert_status=REVOKED` + 释放落地机 |
| 线路机故障切换 | 改对应凭证的 `server_id`,其余不动 |

## 10. 与现状差异 + 迁移步骤

现状:`xray_client`(单表,含身份+落地+状态)+ `trade_subscription.xray_client_id`(1:1)。

迁移(数据仅个位数,零风险):
1. 建 `trade_subscription_certificate`、`trade_traffic_grant`。
2. 搬数据:每个订阅 → 取其 `xray_client` →
   - 建凭证(复用 `xray_client.id` 作凭证 id;subscription_id、server_id、ip_id、auth_user=client_email、auth_secret=client_uuid、source=BASE;cert_status 由原 status 映射:运行→ACTIVE、已停→SUSPENDED、漂移态→ACTIVE);
   - 建 BASE grant(quota=plan.traffic_gb、used=按 ip_id 取 capacity.biz、granted_at=started_at、expires_at=expires_at、status 由订阅状态映射)。
3. 删 `trade_subscription.xray_client_id` + `uk_sub_xray_client`;删旧表 `xray_client`。

## 11. 现阶段做 / 不做(避免过度设计)

**现在做**:迁结构 + 搬数据 + 改造现有代码读新表(node 经 api 写分配 / 拉应运行凭证),**保持当前 1 订阅 : 1 IP : 1 基础额度行为不变**。

**暂不做**(表/字段已备好,逻辑后补):加购 IP、加购流量包、多 grant 扣减优先级、全局对账表、addon 商品目录、`trade_plan.base_ip_count`。
