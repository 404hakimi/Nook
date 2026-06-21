# Agent 拆分 + xray 装机契约(线路机 / 落地机)

> 跨会话对齐文档。后台侧的 `/xray/deploy` 契约已落地(见 commit `a694ebd`);本文给 **nook-agent(Go)会话** 作拆分 + 实现的对齐基准。Go 代码由 agent 会话实现。

## 背景:装机模型已翻转

xray 装机从「后台拼 bash 脚本 → `POST :44844/execute` 远端同步执行」改为
「**后台配置(落 DB 期望态)+ 通知 agent(`POST /xray/deploy` 下发结构化配置)→ agent 用内置 Go 逻辑本地装机**」。

- 后台只负责 DB + 通知,不再远端执行脚本。
- 原 8 个 bash 装机模块(prepare-env/timezone/ufw/acme-tls/logrotate/journald-cap/xray/finalize)已退场,逻辑搬进 agent。移植参考在 git 历史:`git show 0d186ec^:nook-module-node/nook-module-node-server/src/main/resources/scripts/modules/50-xray.sh.tmpl`(及同目录其它模块)。
- 运行时配置(用户/出站/路由)仍是既有 reconcile(agent 轮询拉 desired),**入站一次落地基本不变,不进 reconcile**。

## 持久化分层(关键,决定各份配置存哪)

| 层 | 内容 | 谁写入 | 生命周期 / 恢复 |
| --- | --- | --- | --- |
| **DB**(`xray_install` + `xray_inbound`) | 期望态: 版本 / 路径 / api 端口 / 域名绑定 / inbound 语义参数 | 后台配置时 | **持久**;服务器重置后据此重新 `/xray/deploy` —— 终极真相源 |
| **节点 `config.json`** | 入站结构(`clients:[]` 空) | agent `/xray/deploy` 时写 | 落盘持久;xray 重启自动重载入站 |
| **xray 运行时内存** | 用户(adu)+ 出站(ado)+ 路由(adrules) | agent reconcile 持续灌 | 易失;xray 重启后由 reconcile 重新收敛,不落 config.json |

**恢复路径:**
- xray 重启(机器在):`config.json` 重载入站 → reconcile 重新灌用户/出站/路由。
- 服务器重置(机器擦):后台据 DB 重新 `/xray/deploy` → agent 重装 + 重写 `config.json` → reconcile 收敛。

**因此 `xray_install` 表必须保留**(含路径 / api 端口列):它是持久期望态,重置重建依赖它;且 agent 二进制装机会读 `binaryPath` / `apiPort` 配 `config.yml`。**不可当冗余删**(曾误判为死数据,实为持久配置 + 共享约定)。

## A. 包结构(都在现 `nook-agent/`,一仓两二进制)

```
nook-agent/
  internal/agentcore/    共享: 注册 frontline / heartbeat(1min) / NIC 流量 / 控制 HTTP server(:44844) / config(config.yml) / 后台 API 客户端
  internal/frontline/    线路机: /xray/deploy 内置装机 + reconcile loop(拉 desired, 应用 users/出站/路由 delta)
  internal/landing/      落地机: socks 设置 + 落地侧上报(见 E, 多为 TBD)
  cmd/frontline-agent/   main = agentcore + frontline
  cmd/landing-agent/     main = agentcore + landing
```

原则:只把「业务 handler」分到 `frontline/` `landing/`;基础设施(注册/心跳/NIC/控制 server/config)全留 `agentcore/` 不复制。**不拆成两个独立 repo**(会复制整套 core,必然漂移)。**放当前 monorepo**:agent wire 契约与后台同步演化,同仓便于对齐 + 单 CI。

角色选择:两个 `cmd/` main 共享 `internal/` → **两个二进制、一个仓库、一套 core**。优于「一个二进制 + role flag」(每台机器都带另一角色的码)。

> 现状:nook-agent 已有 `agentcore` + `cmd/landing` 雏形(落地带宽 reconcile 已在)。本结构是**规整/沿用既有骨架**,非从零;agent 会话先核对现有包归属再落。

## B. Wire 契约 — 线路机(frontline)

| 方向 | 端点 | 载荷 | 状态 |
| --- | --- | --- | --- |
| 后台 → agent | `POST :44844/xray/deploy`(Header `X-Agent-Token`) | `XrayDeployRequest`(见下) | **后台已建,agent 待实现** |
| agent → 后台 | `GET /api/agent/reconcile/desired` | desired clients(预渲染 adu/ado/adrules JSON) | 既有,不变 |
| agent → 后台 | `POST /api/agent/heartbeat`(1min) | agentVersion(+ 拟带 xray 状态,见 §状态机) | 既有,**共享(agentcore)** |
| agent → 后台 | `POST /api/agent/nic-traffic`(5min) | rx/tx/biz bytes | 既有,**共享** |
| agent → 后台 | **装机结果回报**(端点 or 心跳,见 §状态机) | `status: ok\|failed` + message + xrayUptime | **本次新增需求,方式待定** |

`/xray/deploy` 响应沿用现 `/execute` 约定:流式回传 stdout,**末行含 `NOOK_RESULT=ok`** 表示成功(无标记 → 后台判失败)。

### XrayDeployRequest(后台下发,agent 据此装机)

```jsonc
{
  "serverId": "...",
  "xrayVersion": "v26.3.27",     // 或 "latest"; agent 据此判幂等
  "forceReinstall": false,        // 即使版本一致也重下
  "enableOnBoot": true,           // systemctl enable xray
  "installUfw": true,
  "setTimezone": true,            // Asia/Shanghai
  "logRotate": true,
  "sharedInboundPort": 443,
  "inboundConfigJson": "<已渲染的 xray inbound JSON>",  // agent 不透明写进 config.json 的 inbounds
  "domain": "x.karsu.cc",         // 可空; 非空才走 acme
  "cfApiToken": "***",            // 可空; acme DNS-01 用
  "timeoutSeconds": 1200
}
```

## C. 共享约定(agent 自有常量,必须与后台渲染的 JSON 一致)

原后台 `XrayInstallDefaults` 常量,装机改造后归 agent 所有;但 agent 落盘/监听的值要与后台 `inboundConfigJson`、reconcile 引用的一致:

- **TLS cert/key 路径**:`/home/xray/tls/cert.pem`、`/home/xray/tls/key.pem`
  —— `inboundConfigJson` 的 `tlsSettings.certificates` 引用它,agent acme 必须落盘到这里。
- **xray api 端口**:`44944`(loopback);reconcile 靠它查 xray、应用 adu/ado/adrules。
- 安装路径:`/home/xray/bin/xray`、`/home/xray/config.json`;systemd unit `/etc/systemd/system/xray.service`。

**这些常量 agent 从哪来**:`binaryPath` + `apiPort` 已由现有 agent 装机写进 `config.yml`(后台从 `xray_install` 读后写入)—— agent 直接用。`cert/key / config / share / log` 路径目前是约定值(= `XrayInstallDefaults`),agent 端硬编码或一并进 `config.yml`,**来源由 agent 会话定**,只需与后台 `inboundConfigJson` 引用一致。

**安全(cfApiToken)**:`/xray/deploy` 走内网或 HTTPS 信任链;agent **日志脱敏**(`cfApiToken=***`)、**用完不持久化**(仅本次 acme 用,不写盘 / 不入 config.yml)。

## D. 线路机装机责任(`/xray/deploy` 内 agent 要做的 = 退场的 bash 模块逻辑)

按 flag / domain 条件执行;移植参考见 git 历史(`git show 0d186ec^:.../scripts/modules/<name>`):

1. **prepare-env**:root 校验、Ubuntu 22+ / arch 校验、**dpkg-lock 等待**、apt 公共依赖(curl/wget/jq/iproute2/unzip/ca-certificates)。
2. **ufw**(`installUfw`):放行 `22` / `sharedInboundPort` / `44844`。
3. **acme-tls**(`domain` 非空):acme.sh + Cloudflare DNS-01;**成本感知复用(现有证书 >7 天有效则不重签,防 Let's Encrypt 限流)**。
4. **logrotate / journald-cap**(对应 flag)。
5. **xray(核心,最重)**:arch 检测(x86_64/aarch64/armv7);按 `xrayVersion` 下载 + **版本 pin**(`forceReinstall` 强制);旧二进制备份;生成 `config.json`(内置 api inbound + **传入的 `inboundConfigJson`** + blackhole/api 出站);`GOMEMLIMIT = 70% 内存`;写 systemd unit;`xray -test` 校验;`systemctl enable/disable`(按 `enableOnBoot`)+ restart;验 xray is-active + api 端口监听。
6. **finalize**:摘要/自检(可选)。
7. **timezone**(`setTimezone`)可选。**swap / bbr 不在装机流程**(它们是后台 `ServerOsOp` 的独立运维 op,bash 模块仍保留在后台)。

### D-2. 何时跑:agent 上机 bootstrap vs xray 部署(本次细化)

机器级准备**不应只在 xray 部署时跑** —— 防火墙/时区等在 **agent 上机(部署 agent)时就该走一遍**,收到 `/xray/deploy` 后**再幂等走一遍**(部署时才知道的入站端口等要补)。划分:

| 步骤 | agent 上机(bootstrap) | xray 部署(`/xray/deploy`) |
| --- | --- | --- |
| prepare-env(apt 依赖) | ✔ 机器一上来备好 | ✔ 幂等确认 |
| timezone(`setTimezone`) | ✔ | ✔ 幂等 |
| ufw | ✔ 基础(22 + 44844) | ✔ 补 `sharedInboundPort`(部署时才知) |
| logrotate / journald-cap | ✔ 机器级 | 可选 |
| swap / bbr | `ServerOsOp` 按需运维 | — 不随 xray |
| acme-TLS | — | ✔ xray 专属(需 `domain`) |
| xray 二进制 / `config.json` / systemd | — | ✔ xray 专属 |

→ "机器级 bootstrap" **已在现有 SSH 装 agent 脚本** `scripts/install/nook-agent.sh.tmpl` 做(时区 / UFW / journald 等已存在,**非新工作**);`/xray/deploy` 只补 xray 专属 + 端口相关(ufw 加 `sharedInboundPort`)。ufw 补端口放 SSH 脚本还是 deploy 时补,由 agent 会话定。**所有步骤必须幂等**(跑两遍安全)。

## E. 落地机(landing)— SOCKS5 部署对齐 xray 方式(暂搁置,第二步)

方向:**SOCKS5 落地部署也改成与 xray 一致的「配置 + 通知 agent」模型** —— 后台落 DB 期望态 + 通知 → agent 用内置逻辑装 dante/socks,取代现在的后台 **SSH** 装(`SOCKS5_INSTALL` 脚本)。同样遵循上面的持久化分层(DB 持久期望态;落地侧的持久/内存边界第二步再定)。

**本期暂搁置**,分两步走:
1. 先把 **frontline** 拆出来接 `/xray/deploy`(契约已就绪)。
2. landing 的 agent 化 + socks `/socks/deploy` 契约(后台 + agent 双侧新设计)留到第二步。

> 注:nook-agent 已有 `cmd/landing` + 落地带宽 reconcile 雏形,本拆分是规整既有骨架;socks 部署对齐(`/socks/deploy`)才是第二步**新增**。开发态,无生产兼容包袱。

## 状态机 + 装机结果回报(后台侧)

`xray_install.install_status`:`deploying`(配置落库即置)→ `ok`(+ `installed_at`)/ `failed`(配置留库,幂等重试)。

**当前已实现 = 纯同步**:`/xray/deploy` 阻塞到 agent 装完,流式回日志,结果回来即 `markInstallStatus(OK/FAILED)`。局域网 / 单机够用。

**提议增强(未实现,待定,需后台 + agent 双侧)**:跨洲长部署若同步连接中断,status 会卡在 `deploying`。补明确回报:
- **(a) 专用端点** `POST /api/agent/xray/install-report {serverId, status, message, xrayUptime}` —— 即时。
- **(b) 心跳带 xray 状态** —— heartbeat req 加 `xrayStatus` 字段,后台据此收敛 —— 无需新端点,**自愈**(漏报下次补);副作用:在线 + xray 健康一起报。
- 倾向 **(b)**。权威规则(防与同步冲突):**心跳只把卡死的 `deploying` 推进到 `ok/failed`,不回退已终态**(同步已置的 ok/failed 优先)。
- 决策前后台保持纯同步;B 表里 heartbeat「拟带 xray 状态」与「装机结果回报」行均属此提议,**未落地**。

## 本契约不规定(agent 会话自决的实现细节)

下列是 agent Go 实现细节,不属跨会话契约,由 agent 会话定;契约只管 **wire 端点+字段 / 持久化边界 / 共享路径约定 / 状态语义**:
- acme 证书有效期检测 / 缓存 / `forceReinstall` 是否重签
- xray 二进制下载源 / checksum 校验 / `latest`→具体版本解析 / 下载重试
- 各步骤幂等的具体做法(检查-跳过 vs 无条件重跑)、部分失败的回滚 / 清理
- agent 首启 config 自检(xray 段缺失时跳过 vs 退出)、热更新
- frontline / landing 内部包分层 / build tags / `/xray/deploy` 总超时与脏数据恢复
