# Xray inbound 多协议方案 (VLESS+Vision+REALITY)

> 评审稿(未定)。解决两件事:① `xray_inbound` 表字段与 vmess+ws 强绑定,无法表达新协议参数;② 服务端配置/重建当前靠后台 SSH push,想收敛成「后台只写 DB,服务端活儿全下沉 agent」。
> 本期目标:共享 inbound 从 vmess+ws 扩展到 **VLESS+Vision+REALITY**;表结构按「通用列 + 协议参数 JSON」重构;部署/重建改 **后台 call agent 接口触发 + agent 本地执行**(见 §6)。
> **范围已拍板**:本期只落地 REALITY(AnyTLS 暂缓,理由见 §4);JSON 存我方语义参数,渲染时翻译(见 §8);部署走 push 触发 + agent 执行(见 §6)。

---

## 1. 名词(先对齐)

- **线路机**:客户端连接的入口机器(跑 xray inbound,公网可达)。**落地机**:出口机器(跑 socks5)。详见 [frontline-failover.md](frontline-failover.md)。
- **agent**:装在每台节点上的 Go 程序(`nook-frontline` / `nook-landing`)。当前**纯出站 client**:只主动连后台(心跳 / 流量 / 拉对账),自己不监听端口。本方案给它新增一个**控制接口**(见 §6)。
- **共享 inbound**:线路机上所有客户共用的一个监听端口 + 协议配置 (`in_shared`);user 靠 `xray api adu` 动态挂进去。
- **协议三元组**:`protocol`(vmess/vless) × `transport`(tcp/ws/grpc) × `security`(none/tls/reality)。当前固定 `vmess × ws × (tls|none)`。
- **REALITY**:XTLS 的抗审查方案 —— 不用自己的域名/证书,而是 TLS 握手时"借用"(偷取)一个真实大站 (`dest`) 的证书链,客户端用预共享的 `publicKey`+`shortId` 验证。免域名、免 acme、无 TLS-in-TLS 指纹。
- **profile**:一台线路机 inbound 的一种完整协议形态。本期两种:`vmess-ws`(现状) 与 `vless-reality`(新增)。
- **desired-state 对账**:后台只写 DB 期望态,agent 周期拉取后把本地 xray 收敛到期望(缺则补、多则删)。**运行态(user/出站/路由)已实现**(见 §6)。

---

## 2. 现状与痛点

`xray_inbound` 表(`server_id` 1:1 `resource_server`)当前字段:

| 字段 | 性质 |
|---|---|
| `protocol` / `transport` / `listen_ip` / `shared_inbound_port` / `domain` | 通用 |
| `ws_path` | **ws 专属** |
| `tls_cert_path` / `tls_key_path` | **文件证书专属(REALITY 不用)** |

痛点是**三条链路都硬编码 vmess+ws**,不是单点:

1. **安装期渲染** — `50-xray.sh.tmpl` 的 `in_shared` 块写死 `protocol:vmess` + `network:ws`,TLS 块按 `USE_TLS` 拼 `tlsSettings`。没有 reality 的位置。
2. **运行期加 user** — `InboundProtocolMapping` 只抽象了 `clients[]`(user 级:vmess/vless/trojan 的 id/flow/password 差异),**完全没碰 `streamSettings`**(传输 + 安全级)。
3. **订阅渲染** — `TradeSubscriptionServiceImpl` 非 vmess 直接 `continue` 跳过,只产 `vmess://` 与 Clash vmess proxy。

REALITY 需要的 `dest / serverNames / privateKey / publicKey / shortIds / fingerprint / flow` 一整套,**当前表一个都没有** —— 所以"扩展存储"方向成立。

另一个现状:**inbound 本身的配置(`in_shared` 协议/streamSettings)目前是装机脚本写死在 config.json 的,不在对账范围**;运行态对账只往这个 inbound 里加减 user(adu/rmu),不重建 inbound 本体。所以换协议/改 REALITY 参数现在只能重装 —— §6 把它纳入"后台触发 + agent 重建"。

---

## 3. 设计原则

- **个位数 server 小规模**,别过度设计:不上版本号/乐观锁、不订阅热加载、不深合并;一张表整段覆盖 + 重启进程(沿用既有风格)。
- **能扁平就扁平,该嵌套才 JSON**:项目里多值字段(`cert.standby_server_ids`)用的是有序 CSV。但 inbound 协议参数是**嵌套异构**(reality 里套 `serverNames[]` / `shortIds[]` + 多个标量),CSV 表达不了,这是少数真正该用 JSON 的地方。
- **存语义、不存内核片段**:JSON 存我方定义的语义字段,服务端 config 渲染和客户端订阅渲染各自从它翻译。不直接透传 xray `streamSettings`(两端不对称:服务端要 `privateKey`、客户端要 `publicKey`;且跨 xray 版本字段漂移没法治理)。
- **后台只写数据,服务端活儿下沉 agent**:协议语义/渲染收口后台,SSH/xray 执行全归 agent(见 §6)。

---

## 4. 决定性约束:xray-core 不支持 AnyTLS(留档)

- AnyTLS 的支持请求 [Xray-core#4428](https://github.com/XTLS/Xray-core/issues/4428) 被官方标 `not planned`,理由:"VLESS + REALITY/xhttp 已覆盖同类抗审查能力"。
- AnyTLS 服务端目前**只有 sing-box / mihomo** 两个内核实现。
- 我们整个系统是 **xray-core 单内核**(`xray api adi/adu/ado`)。要支持 AnyTLS 就得在线路机上**引入 sing-box 第二内核**,即双内核安装 / 双套 user 对账 / 双套运维 —— 工作量是 REALITY 的数倍。

**决策**:本期不做 AnyTLS。表结构与渲染抽象按"多协议可扩展"设计好,给 AnyTLS 留扩展位(未来若引入 sing-box,新增一种 profile + 一套渲染器,不动表结构)。

---

## 5. 协议 × 内核 × 客户端 支持矩阵

| 协议 | xray 服务端 | 客户端支持面 |
|---|---|---|
| **VLESS+Vision+REALITY** | ✅ 原生(旗舰) | 宽:v2rayN/NG、sing-box/NekoBox、**mihomo/Clash.Meta**、Shadowrocket、Stash、Hiddify…<br>⚠️ **原版 Clash 不支持 REALITY** |
| AnyTLS | ❌ | 较窄:sing-box(1.12+)、mihomo、v2rayN(7.14.3+)、Hiddify |
| vmess+ws(现状) | ✅ | 最宽,但易识别、无抗审查优势 |

**连带影响**:订阅现产**通用 Clash YAML**。上 REALITY 后,原版 Clash 用户用不了 reality 节点,Clash 那一路的目标客户端必须确认是 **mihomo/Clash.Meta**;否则 reality 节点只能走 `vless://` URI 给 v2rayN/sing-box 系。这点要在 §10 与客户端侧对齐。

---

## 6. 部署与下发架构(push 触发 + agent 执行)

### 6.1 三类操作,各走各的模式

| 操作 | 模式 | 谁干活 | 状态 |
|---|---|---|---|
| 日常运行态(客户 user / 出站 / 路由) | agent **pull 对账** | agent 本地 `xray api adu/ado/adrules` | ✅ 已实现 |
| 事件:装机 / 换协议 / 重建 inbound | 后台 **call agent 接口**触发 | agent 本地跑脚本 + `xray api adi/rmi` | 🆕 本方案 |
| 录服务器 / 选协议 / 存 params·密钥 | 后台只写 DB | 后台 | — |

后台职责收敛成:**录数据 + 触发通知 + 收对账**;SSH/xray 重活全在 agent。

### 6.2 决策:事件用「后台 call agent 接口」触发,不用版本号

- agent 新增一个**控制接口**(HTTP server),后台需要装机/重建时直接 call 它,agent 收到即本地执行。
- **不引入心跳版本号触发**:版本号那套(心跳响应带 desired 版本、agent 自己发现该重对账)只在"坚持 agent 纯出站、不开接口"时才需要。本方案放开了 agent 开接口,后台直接 call 就是最直接的触发,版本号是多余层 —— 砍掉。
- 运行态对账仍是 agent **pull**(不动);只有"事件性重建"走这个 push 接口。两者并存:**会变的状态拉,一次性事件推。**

### 6.3 前提与风险(已拍板)

- **可达性(硬前提)**:后台要能 call 到 agent,要求**节点对后台网络可达**。机场场景节点都是公网 VPS(线路机本就是客户要连的公网入口),前提成立。
  > ⚠️ 留档:若将来出现 NAT 后/无公网入站的节点,该节点的"事件触发"要回退到 pull(心跳版本号那套);本期不考虑。
- **攻击面**:agent 开控制接口 = 多一个入站控制面。**已接受**(项目主决策)。加固(必做,见 6.4 ②)把风险压到很小。

### 6.4 四个落地点(必须先定)

① **bootstrap 仍是 SSH**:agent 自己得先上机器才能被 call。装 agent 仍走后台 SSH push 一次(现有 `AgentInstallScriptServiceImpl`)。SSH 链路从"装 xray"**收缩**成"装 agent",之后 xray 全归 agent。

② **控制接口加固**:复用现有 `agent_token` 反向鉴权(后台 call 时带 token,agent 校验)+ 装机时 UFW 只放行后台出口 IP 访问该端口。简单够用,不上 mTLS(规模小,别过度)。

③ **进度/结果回传(别丢"看装机日志")**:控制接口**只立即返回"已受理"**,别同步阻塞等装机跑完(几十秒~分钟,HTTP 易超时)。装机异步在 agent 本地跑,**过程日志 + 最终结果走 agent 出站上报**回后台(沿用现有上报通道),前端进度由出站上报驱动。即:**触发用 push(后台→agent),进度/结果用 agent 出站回传。**

④ **渲染分工:后台渲染语义,agent 当执行器**:协议→xray JSON 的翻译收口后台(§9),后台把渲染好的 inbound JSON 给 agent,agent 只 `adi`/重建,不懂协议语义。**唯一例外是 x25519 密钥** —— 装机时 agent 本地 `xray x25519` 生成后回传后台落库(§11)。

### 6.5 脚本下沉:embed bash,不 Go 重写

"把 xray 安装脚本给 agent"建议让 agent **内嵌现有 bash 模块**(45-acme / 50-xray / UFW…)本地 exec,只换"执行位置(后台 SSH → agent 本地)"和"触发方式(点按钮 → 后台 call)",脚本本身基本不动。Go 重写工作量大且无必要,embed 保住现有资产。

---

## 7. 表结构改造

保留通用列,新增一个协议参数 JSON 列,删掉与 ws/文件证书强绑定的列(并入 JSON)。

```
xray_inbound
  server_id            varchar   PK (1:1 resource_server)        【保留】
  protocol             varchar   vmess / vless                   【保留】
  transport            varchar   tcp / ws / grpc                 【保留】
  listen_ip            varchar                                   【保留】
  shared_inbound_port  int                                       【保留】
  domain               varchar   vmess+ws 的 CDN CNAME;reality 不用  【保留(可空)】
  security             varchar   none / tls / reality            【新增】← 从隐式拆出来显式存
  params               json      协议/传输/安全的细节语义参数(见 §8) 【新增】
  ws_path              ───────── 并入 params.ws.path             【删除】
  tls_cert_path        ───────── 并入 params.tls.certPath        【删除】
  tls_key_path         ───────── 并入 params.tls.keyPath         【删除】
  created_at / updated_at                                        【保留】
```

- `security` 单拆成列(不塞 JSON):它和 `protocol`/`transport` 一样是 inbound 的"形态主键",渲染分派、列表展示、对账都要直接读,值得当列。
- `params` 用 MySQL 原生 `JSON` 列类型,**minified 存储,不额外 gzip/base64**(个位数 server 省不了存储,压缩后不可 SQL 查、对账日志看不懂、排障要先解码)。
- 新表显式 `COLLATE utf8mb4_general_ci`(全库一致,避免 MySQL8 新表默认 `_0900_ai_ci` 跨表比较报 1267)。

**MyBatis 映射(项目无 TypeHandler 先例,定调)**:`XrayInboundDO.params` 映射成 **`String`**,应用层用 fastjson2(项目已用 `com.alibaba.fastjson2`)parse 成语义对象 `InboundParams`。不引 `JacksonTypeHandler`/自定义 TypeHandler —— 渲染/订阅本就要拿到结构化对象再翻译,String + 显式 parse 比 TypeHandler 黑盒更可控,也契合"别过度设计"。

---

## 8. params JSON schema(我方语义)

顶层按 `transport` / `security` / 协议 分块。三种 profile 示例:

**vmess-ws(现状,迁移后)**
```json
{"ws":{"path":"/abc123"},
 "tls":{"certPath":"/home/xray/tls/cert.pem","keyPath":"/home/xray/tls/key.pem"}}
```
> `security=tls` 时有 `tls` 块;无域名纯 ws 时省略 `tls`。

**vless-reality(新增)**
```json
{"flow":"xtls-rprx-vision",
 "reality":{"dest":"www.microsoft.com:443",
            "serverNames":["www.microsoft.com"],
            "privateKey":"<x25519-private>",
            "publicKey":"<x25519-public>",
            "shortIds":["","0123456789abcdef"],
            "fingerprint":"chrome"}}
```

字段说明:
- `flow` — VLESS 专属,固定 `xtls-rprx-vision`;渲染进 `clients[].flow` 与订阅 `flow=` 参数。
- `reality.dest` / `serverNames` — 偷取的目标真站 + SNI;**运维选址**:要选支持 TLS1.3+H2、目标地区可达、且 `serverNames` 与 `dest` 证书匹配的站。
- `privateKey` / `publicKey` — x25519 密钥对(§11)。private 进服务端 `realitySettings`,public 进订阅 `pbk=`。**两端不对称,所以必须我方存语义、各自翻译**(印证 §3 第三条)。
- `shortIds` — 服务端允许的 shortId 列表(含空串);订阅任选一个进 `sid=`。
- `fingerprint` — uTLS 指纹,**纯客户端参数**(服务端 `realitySettings` 不需要),只供订阅 `fp=` / Clash `client-fingerprint`。

> Java 侧对应 `InboundParams { WsParams ws; TlsParams tls; String flow; RealityParams reality; }`,按 profile 只填相关块。

---

## 9. inbound 渲染与下发(后台渲染语义 + agent 执行)

按 §6.4 ④ 的分工:**协议→xray JSON 的翻译收口后台一处**,agent 只执行。

**后台**:按 `protocol × transport × security` + `params` 渲染出完整的 `in_shared` inbound JSON(streamSettings + 空 clients 壳)。把 `InboundProtocolMapping`(现在只管 `clients[]`)扩出 `streamSettings` 渲染,落点待定其一:
- 扩 `InboundProtocolMapping`:增 `buildStreamSettings(InboundParams, transport, security)`;或
- 新增 `InboundStreamRenderer`(按 transport×security 分派),与 `InboundProtocolMapping`(按 protocol 分派 clients)分工。

**agent**:收到后台控制接口通知(或对账)后,本地 `xray api rmi in_shared` + `adi <json>` 重建 inbound —— 换协议/改 reality 参数**免重装机**。user 名单仍走现有运行态对账(clients 的 adu/rmu),与 inbound 本体重建解耦。

> 同一个后台渲染器,也供装机时生成初始 `in_shared`(取代 `50-xray.sh.tmpl` 里 shell heredoc 硬编码 —— shell 拼 reality 嵌套数组极易错)。

vless-reality 的服务端 `streamSettings` 目标形态:
```json
"streamSettings":{"network":"tcp","security":"reality",
  "realitySettings":{"show":false,"dest":"www.microsoft.com:443","xver":0,
    "serverNames":["www.microsoft.com"],"privateKey":"<private>",
    "shortIds":["","0123456789abcdef"]}}
```
clients[]:`{"id":"<uuid>","flow":"xtls-rprx-vision","email":"<email>","level":0}`;settings 仍需 `"decryption":"none"`。

---

## 10. 订阅渲染改造(客户端)

`TradeSubscriptionServiceImpl` 增 vless-reality 分支(保留 vmess 分支)。

**`vless://` URI**(给 v2rayN / sing-box / Shadowrocket 系):
```
vless://<uuid>@<host>:<port>?encryption=none&flow=xtls-rprx-vision
       &security=reality&sni=<serverName>&fp=<fingerprint>
       &pbk=<publicKey>&sid=<shortId>&type=tcp#<remark>
```

**Clash.Meta(mihomo)proxy**:
```yaml
- name: <node>
  type: vless
  server: <host>
  port: <port>
  uuid: <uuid>
  network: tcp
  udp: true
  tls: true
  flow: xtls-rprx-vision
  servername: <serverName>
  client-fingerprint: <fingerprint>
  reality-opts:
    public-key: <publicKey>
    short-id: <shortId>
```

注意:
- `host` 用线路机出网 IP 或域名;REALITY 不依赖自有域名,IP 直连即可(SNI 走 `serverName` 伪装)。
- `sid` 从 `shortIds` 任取(非空优先);`pbk` 取 `params.reality.publicKey`。
- **原版 Clash 兼容性**:reality 节点只渲进 mihomo/Clash.Meta 格式。需与客户端侧确认目标内核(§5 连带影响)。

---

## 11. REALITY 密钥生成与落库

REALITY 需要一对 x25519 密钥 + 一组 shortIds,**部署/重建 reality inbound 时生成、随 inbound 落库**(private 进 config、public 进订阅)。按 §6 的 agent 执行器架构:

- **首选(agent 生成回传)**:agent 本地有 xray binary,装/重建 reality inbound 时跑 `xray x25519` 生成密钥对,通过**出站上报**回填后台 `params`。格式天然对(REALITY 要的 base64url raw32),与"agent 执行器"一致。
- **备选(后台预生成)**:后台用 JDK 内置 `KeyPairGenerator.getInstance("X25519")` 生成,提取 raw 32 字节编码 base64url,落库 + 随通知下发。装机前即定、不依赖 agent 回传;但要自己处理 PKCS8→raw32 编码,需校验与 `xray x25519` 格式一致。

`shortIds`:随机 hex(偶数长度 ≤16)生成,含一个空串;agent 或后台生成均可,落库即可。

> 倾向**首选**(对齐 agent 执行器,密钥格式零转换);若想"装机前 params 即完整"再考虑备选。待定。

---

## 12. 现有数据迁移

现网都是 vmess-ws。一次性迁移(个位数 server,无需双写过渡):

1. 加列 `security`、`params`(JSON,可空)。
2. 回填:`security` 按有无 cert 路径取 `tls`/`none`;`params` 由旧 `ws_path`/`tls_cert_path`/`tls_key_path` 拼成 `{"ws":{...},"tls":{...}}`。
3. 验证渲染/订阅产出与迁移前一致(vmess 链接不变)后,删旧三列。

REALITY 是**新装/重装**线路机时才走的新 profile,不影响存量 vmess 机的连接。

---

## 13. 分期落地

1. **存储层**:表加 `security`+`params`、`XrayInboundDO`/`XrayInboundDTO` 改字段、迁移回填、删旧列。产出与现状等价(纯 vmess,零行为变化)。
2. **下发架构**:agent 加控制接口(后台 call 触发,token 鉴权)+ 进度出站回传 + agent 本地 `adi/rmi` 重建 inbound。**先用现有 vmess+ws 跑通**"后台触发 → agent 重建 `in_shared`"链路(不涉协议变更,纯验证通道)。
3. **服务端渲染 + REALITY**:后台收口 `streamSettings` 渲染(reality 形态),x25519 agent 生成回传,装机表单放开 protocol/security(当前置灰);vless-reality 可装可起(config -test 过)。
4. **订阅渲染**:`vless://` + Clash.Meta reality;与客户端侧确认内核后放开。
5. **(更大重构,可后置)整机装机下沉 agent**:把 `50-xray` 等 bash embed 进 agent,装机也走后台触发,SSH 仅留 agent bootstrap。
6. **(未来)AnyTLS**:引入 sing-box,新增 profile + 渲染器,不动表结构。

---

## 待定清单(打磨项)

- [ ] §6 agent 控制接口契约:端点形态(立即受理 + 异步出站回传)、token 反向鉴权细节、UFW 放行后台 IP。
- [ ] §6.5 / §13 脚本下沉粒度:先做 inbound 重建下沉(支撑 REALITY 免重装),整机装机下沉后置 vs 一步到位。
- [ ] §9 渲染抽象落点:扩 `InboundProtocolMapping` vs 新增 `InboundStreamRenderer`。
- [ ] §11 密钥生成 首选(agent `xray x25519` 回传)vs 备选(后台 JDK 生成);x25519 base64url 编码对齐验证。
- [ ] §10 Clash 一路目标内核(mihomo)确认;原版 Clash 用户的降级策略(是否还给 vmess 兜底)。
- [x] `dest`/`serverNames` 选址 **已决**:预置候选站 (微软 / 苹果 / Cloudflare / 亚马逊 / 必应),装机时后台下拉可选 (`RealityDestPreset` 枚举);不固定单站、也不强制手填。
- [ ] 一台线路机是否允许多 profile 并存(当前假设 1 机 1 profile)。
- [ ] 节点可达性前提:确认全为公网 VPS;NAT 节点的 pull 回退是否需要现在就留口子。
