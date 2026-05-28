# 05-Agent 架构与运行时配置 (现状)

> Go agent 设计 + 任务队列 + 装机 + config 同步。Go 开发者必读, 运维参考。
> 标记: ✅ 已实现 / 🟡 部分 / ❌ 待建 (见 [06-Roadmap](./06-Roadmap-待建.md))。

---

## 1. 设计目标 ✅

把后端 SSH 主动拉数据 / 推配置, 反转为**服务器主动 push + 拉任务**:

- **数据流方向反转**: 数据 server → backend (HTTPS POST), 不再 SSH 拉
- **配置变更走任务队列**: backend 写 task, agent 轮询拉 + 执行 + 回报
- **配置驱动**: agent 行为由后端推的 yaml 决定
- **单一二进制**: 线路机 / 落地机同一套代码, 行为靠角色 + 配置区分

**关于落地机 agent (现状)**:
- 线路机 (frontline) 和落地机 (landing) **都已装 agent + 有 agent_token + 上心跳**
- frontline binary 跑 xray executor + xray-stats collector; **landing binary 的 socks5 executor 仍是空壳** (`cmd/landing/main.go` 的 `registerLanding` 返回空), 只共用 heartbeat / nic / poller / upgrade / config_reload
- 落地机限速 executor (socks5_set_bandwidth) 未实现, 见 [06-Roadmap §6](./06-Roadmap-待建.md)

---

## 2. 实现语言: Go ✅

- agent 职责简单 (采集 push + 拉任务 + 调本地 xray API), 二进制 ~10MB, 占用 5-15MB
- 单二进制部署, 无 runtime 依赖, 启动 < 100ms
- Backend (Java) 定义 HTTP API 契约 + 完整校验; agent (Go) 实现客户端
- DTO 不共享代码 (Go struct + JSON tag 对齐)

仓库: `nook-agent/` (cmd/frontline + cmd/landing + internal/{client,poller,executor,heartbeat,nic,xray,config,agentcore})。

---

## 3. Agent 内部结构 ✅

```
nook-agent (Go 单二进制)
├─ 内核 (agentcore)
│   ├─ config loader   (读 /home/nook-agent/config.yml)
│   ├─ scheduler       (按 interval 跑 collectors)
│   ├─ poller          (定时 GET task queue, 60s)
│   ├─ client          (HTTPS POST 数据, 带 X-Agent-Token)
│   └─ executor dispatcher
│
├─ Collectors
│   ├─ heartbeat ✅    — 1min 心跳 + agent_version
│   ├─ nic (vnstat) ✅ — NIC 流量采样 5min
│   └─ xray-stats ✅   — xray statsquery (frontline only, 5min)
│
└─ Executors
    ├─ ping ✅          — echo 探活, 返回 {"pong":true}
    ├─ xray ✅          — provision_user / remove_user / update_outbound (frontline only)
    ├─ config_reload ✅ — 原子写 config.yml + os.Exit(0) → systemd 拉新进程
    └─ agent_upgrade ✅ — 下载新 binary + sha256 校验 + 替换 + os.Exit(0)
```

> landing binary 注册的 executor 不含 xray (空 goroutine 列表); 共用 ping / config_reload / agent_upgrade。

---

## 4. 配置模板 (backend 渲染)

### 4.1 线路机 (frontline, xray)

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: ${resource_server.id}
  agent_token: ${64 char hex token}
  timeout_seconds: 60
heartbeat:
  interval_seconds: 60
nic:
  interval_seconds: 300
  interface: eth0
xray:
  enabled: true
  api_endpoint: 127.0.0.1:10085
  stats_interval_seconds: 300
poller:
  interval_seconds: 60
```

### 4.2 落地机 (landing, socks5)

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: ${resource_server.id}     # 落地机也是 resource_server, 用 server.id
  agent_token: ${resource_server.agent_token}
heartbeat:
  interval_seconds: 60
nic:
  interval_seconds: 300
  interface: eth0
# xray.enabled 默认 false; dante 限速 executor 待建 (见 06-Roadmap)
poller:
  interval_seconds: 60
```

> agent_task 用 `agent_type` (frontline/landing) + `source_id` (= resource_server.id) 区分任务目标, 不是旧设计臆想的 host_type/host_id。

---

## 5. Backend HTTP API ✅

```
[Agent → Backend, 鉴权 X-Agent-Token]
  POST /api/agent/heartbeat      心跳 + agent_version 自报
  POST /api/agent/nic-traffic    vnstat 周期累计
  POST /api/agent/xray-traffic   xray stats 累计 (delta)
  GET  /api/agent/tasks          轮询任务 + CAS markPicked
  POST /api/agent/task-result    任务结果回报

[Admin → Backend]
  POST /admin/agent/install-agent (流式)   装机
  GET  /admin/agent/get-install-meta       装机元信息
  POST /admin/agent/upgrade-agent          派升级 task
  GET  /admin/agent/page-task              任务历史分页
  GET  /admin/agent-runtime-config/get-config   查 yaml + 同步状态
  PUT  /admin/agent-runtime-config/save-config  保存 yaml + 派 config_reload task
```

鉴权: `X-Agent-Token` (64 char SHA256 hex, 每 server 独立); `AgentAuthService` 反查 server_id, `@AuthenticatedAgent` 注入 controller。

---

## 6. 任务队列

### 6.1 表结构 ✅

见 [02-数据模型 §5.2](./02-数据模型.md) `agent_task` (`agent_type` + `source_id` + `task_type` + `task_payload` + status)。

### 6.2 任务类型 (现状 6 种) ✅

| task_type | payload | executor | 角色 |
|---|---|---|---|
| `ping` | `{}` | ping | 通用 |
| `agent_upgrade` | `{url, sha256, version}` | upgrade | 通用 |
| `config_reload` | `{yaml, md5}` | config_reload | 通用 |
| `xray_provision_user` | `{inboundJson}` (含 user) | xray: AddUser | frontline |
| `xray_remove_user` | `{inboundTag, email}` | xray: RemoveUser | frontline |
| `xray_update_outbound` | `{oldOutboundTag, newOutboundJson}` | xray: RemoveOutbound + AddOutbound | frontline |

> 注: admin 手动开客户端 (provision/revoke/rotate) 当前走 op 框架 + SSH CLI (见 [03 §3](./03-业务核心-算法与流程.md)), **不经 agent task**。task 路径预留给 portal 自助 / 自动化。限速 task (socks5_set_bandwidth / server_set_bandwidth) 未实现, 见 [06-Roadmap §6](./06-Roadmap-待建.md)。

### 6.3 任务流转 ✅

状态机 (`AgentTaskStatus`): `PENDING → PICKED → SUCCESS / FAILED`。

```
[backend] INSERT agent_task (status=PENDING)
[agent 60s 轮询] GET /api/agent/tasks → 后端 SELECT PENDING LIMIT N + CAS markPicked
[agent 执行] dispatch executor → POST /task-result (SUCCESS / FAILED + output)
[backend] SUCCESS 标完成 (config_reload 还回写 applied_at + applied_yaml_md5)
          FAILED retry_count+1 重置 PENDING (最多 N 次)
```

> 超时兜底 (PICKED > 10min 重置 PENDING) ❌ 未实现。

---

## 7. 部署流程 (装机) ✅

`POST /admin/agent/install-agent` (流式 SSE) → `AgentInstallScriptServiceImpl.installStreaming`:

```
1. 校验 server 存在 + SSH 凭据 + 装机前置
2. 渲染 config.yml (admin 填的 backend URL / timeouts / xray 参数)
3. SSH ad-hoc 跑 nook-agent.install.sh.tmpl:
   - curl 下载 binary (backend public URL)
   - 写 /home/nook-agent/{nook-agent, config.yml}
   - 写 systemd unit + daemon-reload + enable --now
4. 等 1min 看 backend 收到首次心跳 (runtime.last_heartbeat_at 非 null)
5. admin 手动切 lifecycle → READY → LIVE
```

systemd unit `RestartSec=10s` 给自杀重启留窗口 (config_reload / agent_upgrade 跑完 `os.Exit(0)`)。

Agent token: 装机时 backend 生成 `resource_server.agent_token = SHA256(UUID+UUID)` 一次性写入; 泄露只影响该 server。

---

## 8. Runtime Config 同步 (config_reload task) ✅

**核心**: admin 改完 yaml → 派 config_reload task → agent ≤60s 拉到 → 写盘 + 自重启。无新通道, 无分层配置, 无版本号。

### 8.1 同步状态判定 (`AgentConfigSyncState`)

- `applied_yaml_md5 == md5(config_yaml)` → SYNCED
- 行不存在 → NEVER_CONFIGURED
- 其他 → PENDING

### 8.2 数据流

```
Admin 改 yaml
  → PUT /admin/agent-runtime-config/save-config
      ① yaml 语法校验 (snakeyaml)
      ② UPSERT agent_runtime_config (yaml + updated_at)
      ③ INSERT agent_task (config_reload, payload={yaml, md5}, PENDING)
  ⋯ ≤60s ⋯
  → agent poller 拉到 → ConfigReloadExecutor:
      md5 校验 → 原子写 config.yml → go func(){ sleep 1s; os.Exit(0) }
  → POST /task-result SUCCESS {md5}
      → AgentRuntimeConfigService.onConfigReloadSuccess: 更新 applied_at + applied_yaml_md5
  → agent 进程退出 → systemd RestartSec=10s 拉新进程 → 读新 yaml → 首次心跳
  → UI 刷新看 ✅ 已同步
```

### 8.3 边角

- **校验**: backend 只 yaml 语法校验, 不校字段 (避免 backend↔agent 字段双向耦合)
- **MD5**: payload 附 md5, agent 写盘前校验
- **agent 离线**: task 留 PENDING, 上线自动拉
- **连改 N 次**: 派 N 个 task, agent 串行处理, 最终一致 (最后一次 ack 写 applied_md5)
- **xray/socks5 不受影响**: agent 进程退出/拉起 ~1-2s, xray/socks5 独立进程, 流量不中断

---

## 9. 安全设计 ✅

- **Token 鉴权**: 每 server 一个 64 char SHA256 hex, 反查 server_id
- **HTTPS only**: 所有 API 必须 TLS
- **配置审计**: agent 收 config_reload 上报 md5, backend 比对
- **任务幂等**: 所有 task 可重复执行不出错 (add user 重复 = 已存在不报错)

---

## 10. SSH 模式 vs Agent 模式

| 维度 | 旧 SSH 模式 | Agent 模式 (v3) |
|---|---|---|
| 后端 SSH 连接 | 维护到 N 台 server 的连接池 | 仅首次装机 |
| 数据采集 | 后端调度 SSH 拉 | Agent 主动 POST |
| 配置变更 | SSH push (实时) | task 队列 + 轮询 (30s-1min) |
| NAT 后服务器 | 要打洞 | 天然支持 (主动出站) |
| 故障检测 | 5-10min SSH 探活 | 1-3min 心跳超时 |
| 单 server 占用 | 0 (被动) | 5-15MB RAM |

> 注意: 当前 xray client provision/revoke 仍是 op 框架 + SSH CLI (见 [03 §3](./03-业务核心-算法与流程.md)), 不是纯 agent task。两种通路并存: 运维操作走 SSH (实时 + 进度), 心跳/流量/配置走 agent。

---

## 11. 待建扩展 (见 [06-Roadmap](./06-Roadmap-待建.md))

- 落地机 socks5 executor (当前空壳) + 限速 `socks5_set_bandwidth` task
- 线路机限速 `server_set_bandwidth` task (tc qdisc)
- `xray_full_sync` task (远端 dump + diff + 自动修复)
- agent 任务超时兜底 Job (PICKED > 10min 重置)
- provision 改走 agent task (portal 自助场景)
