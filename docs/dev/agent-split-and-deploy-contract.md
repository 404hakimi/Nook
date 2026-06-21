# Agent 拆分 + xray 装机契约(线路机 / 落地机)

> 跨会话对齐文档。后台侧的 `/xray/deploy` 契约已落地(见 commit `a694ebd`);本文给 **nook-agent(Go)会话** 作拆分 + 实现的对齐基准。Go 代码由 agent 会话实现。

## 背景:装机模型已翻转

xray 装机从「后台拼 bash 脚本 → `POST :44844/execute` 远端同步执行」改为
「**后台配置(落 DB 期望态)+ 通知 agent(`POST /xray/deploy` 下发结构化配置)→ agent 用内置 Go 逻辑本地装机**」。

- 后台只负责 DB + 通知,不再远端执行脚本。
- 原 8 个 bash 装机模块(prepare-env/timezone/ufw/acme-tls/logrotate/journald-cap/xray/finalize)已退场,逻辑搬进 agent。移植参考在 git 历史:`git show 0d186ec^:nook-module-node/nook-module-node-server/src/main/resources/scripts/modules/50-xray.sh.tmpl`(及同目录其它模块)。
- 运行时配置(用户/出站/路由)仍是既有 reconcile(agent 轮询拉 desired),**入站一次落地基本不变,不进 reconcile**。

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

## B. Wire 契约 — 线路机(frontline)

| 方向 | 端点 | 载荷 | 状态 |
| --- | --- | --- | --- |
| 后台 → agent | `POST :44844/xray/deploy`(Header `X-Agent-Token`) | `XrayDeployRequest`(见下) | **后台已建,agent 待实现** |
| agent → 后台 | `GET /api/agent/reconcile/desired` | desired clients(预渲染 adu/ado/adrules JSON) | 既有,不变 |
| agent → 后台 | `POST /api/agent/heartbeat`(1min) | agentVersion | 既有,**共享(agentcore)** |
| agent → 后台 | `POST /api/agent/nic-traffic`(5min) | rx/tx/biz bytes | 既有,**共享** |

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

## D. 线路机装机责任(`/xray/deploy` 内 agent 要做的 = 退场的 bash 模块逻辑)

按 flag / domain 条件执行;移植参考见 git 历史(`git show 0d186ec^:.../scripts/modules/<name>`):

1. **prepare-env**:root 校验、Ubuntu 22+ / arch 校验、**dpkg-lock 等待**、apt 公共依赖(curl/wget/jq/iproute2/unzip/ca-certificates)。
2. **ufw**(`installUfw`):放行 `22` / `sharedInboundPort` / `44844`。
3. **acme-tls**(`domain` 非空):acme.sh + Cloudflare DNS-01;**成本感知复用(现有证书 >7 天有效则不重签,防 Let's Encrypt 限流)**。
4. **logrotate / journald-cap**(对应 flag)。
5. **xray(核心,最重)**:arch 检测(x86_64/aarch64/armv7);按 `xrayVersion` 下载 + **版本 pin**(`forceReinstall` 强制);旧二进制备份;生成 `config.json`(内置 api inbound + **传入的 `inboundConfigJson`** + blackhole/api 出站);`GOMEMLIMIT = 70% 内存`;写 systemd unit;`xray -test` 校验;`systemctl enable/disable`(按 `enableOnBoot`)+ restart;验 xray is-active + api 端口监听。
6. **finalize**:摘要/自检(可选)。
7. **timezone**(`setTimezone`)可选。**swap / bbr 不在装机流程**(它们是后台 `ServerOsOp` 的独立运维 op,bash 模块仍保留在后台)。

## E. 落地机(landing)— 契约多为 TBD

- 现状:SOCKS5 由后台 **SSH** 装(`SOCKS5_INSTALL` 脚本),**落地机目前没有 agent 端点契约**。
- 拆分时先定:落地机**要不要也跑 agent**?若要 → 给 `landing/` 加 socks 设置 handler + 落地上报(NIC/quota),**后台侧 contract 尚未建,需要新设计(后台 + agent 双侧)**。
- 建议分两步:① 先把 **frontline** 拆出来接 `/xray/deploy`(契约已就绪);② landing 的 agent 化作为第二步单独设计。

## 状态机(后台侧,供 agent 回报对齐)

`xray_install.install_status`:`deploying`(配置落库即置)→ agent 装机成功后台置 `ok`(+ `installed_at=now`)/ 失败 `failed`(配置留库,幂等重试)。当前后台**靠 `/xray/deploy` 同步流式结果**判成功,无需 agent 单独回报;若后续改异步,再加 `POST /xray/install-report`。
