# 05-Agent 架构与运行时配置

> Go agent 设计 + 任务队列 + 部署流程 + yaml 配置同步. Go 开发者必读, 运维参考.
> 本文合并了原 05-Agent架构 + 08-Agent参数化配置设计.
> 标记: ✅ 已实施 / 🟡 部分实施 / ❌ 待实施

---

## 1. 设计目标

把现在后端 SSH 主动拉数据 / 推配置的模式, 反转为**服务器主动 push + 拉任务**.

- **数据流方向反转**: 数据 server → backend (HTTPS POST), 不再后端 SSH 拉
- **配置变更走任务队列**: backend 写 task, agent 轮询拉 + 执行 + 回报
- **配置驱动**: agent 行为完全由后端推的 yaml 决定, 加新能力 = 加模块 + 改模板
- **单一二进制**: 一个 nook-agent 部署所有 server (线路机 / 落地 IP 同一份), 行为靠配置区分

---

## 2. 实现语言: Go ✅

**选择 Go 的理由**:
- Agent 职责简单 (采集 push + 拉任务 pull + 调本地 xray API), 不涉及复杂业务逻辑
- 二进制 5-10MB, 1C1G 占用 5-15MB, 极轻量
- 单二进制部署, 无 runtime 依赖
- 启动快 (< 100ms), 重启代价低
- 后端 Java 负责接口校验 / 业务逻辑, agent 只做执行器

**Java ↔ Go 协作**:
- Backend (Java) 定义 HTTP API 契约
- Agent (Go) 实现客户端, 按契约调用
- DTO 不共享代码 (Go struct + JSON tag 对齐, 工作量小)
- 后端 Java 完整校验请求 (字段 / 范围 / 鉴权), agent 只发数据

仓库结构: `nook-agent/` ~600 行 Go 源码 (collectors + executors + http client).

---

## 3. Agent 内部结构 ✅

```
nook-agent (Go, 单二进制 ~10MB)
├─ 内核
│   ├─ config loader      (读 /home/nook-agent/config.yml)
│   ├─ scheduler          (按 interval 跑 collectors)
│   ├─ poller             (定时 GET task queue)
│   ├─ reporter           (HTTPS POST 数据)
│   └─ executor dispatcher
│
├─ Collectors (按 config 启用)
│   ├─ heartbeat ✅     — 1min 一次心跳 + agent_version 上报
│   ├─ vnstat ✅         — NIC 流量采样 (5min)
│   ├─ xray-stats ✅     — xray statsquery (per user up/down, 5min, xray.enabled=true 才启用)
│   ├─ xray-dump ❌      — xray api 拉当前用户列表 (配置对账)
│   ├─ dante-stats ❌    — dante 连接数 / 流量
│   ├─ systemd ❌        — systemd 服务状态 (xray/dante)
│   └─ procinfo ❌       — CPU/Mem/Disk
│
└─ Executors (按 config 启用)
    ├─ ping ✅            — echo, 探活
    ├─ xray-api ✅        — xray api adu / rmu / ado / rmo
    ├─ config_reload ✅   — 写盘 /home/nook-agent/config.yml + 自杀 → systemd 拉新进程
    ├─ agent_upgrade ✅   — 下载新 binary + sha256 校验 + 替换 + 自杀
    ├─ systemd-ctl ❌     — systemctl start/stop/restart (白名单)
    ├─ shell ❌           — 任意 shell (admin 远程命令, 白名单)
    ├─ file-write ❌
    └─ file-read ❌
```

---

## 4. 配置模板 (backend 渲染)

### 4.1 线路机 (Frontline, xray)

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: srv-jp-01
  agent_token: ${生成的 64 char hex token}
  timeout_seconds: 60

heartbeat:
  interval_seconds: 60

nic:
  interval_seconds: 300
  interface: eth0           # auto = /proc/net/route 默认出口

xray:
  enabled: true
  api_endpoint: 127.0.0.1:10085
  stats_interval_seconds: 300

poller:
  interval_seconds: 60
```

### 4.2 落地 IP (Landing, socks5)

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: ip-jp-1
  agent_token: ${...}

heartbeat:
  interval_seconds: 60

nic:
  interval_seconds: 300
  interface: eth0

# xray.enabled 默认 false, 不跑 xray collector/executor
poller:
  interval_seconds: 60
```

---

## 5. Backend HTTP API ✅

```
[Agent → Backend POST]
  POST /api/agent/heartbeat              ✅ 心跳 + agent_version 自报
  POST /api/agent/nic-traffic            ✅ vnstat 周期累计
  POST /api/agent/xray-traffic           ✅ xray stats 累计 (delta)
  POST /api/agent/task-result            ✅ 任务执行结果回报

[Agent → Backend GET]
  GET  /api/agent/tasks?server_id=X      ✅ 轮询任务 + CAS markPicked
  GET  /api/agent/runtime-config         ✅ 拉最新 yaml (config_reload task payload 直接带, 这接口备用)

[认证] X-Agent-Token header (64 char sha256 hex, 每 server 独立)
  ✅ AgentAuthService 校验 → 解析出 server_id 注入 controller (AuthenticatedAgentArgumentResolver)
```

详见 [02-数据模型 §2.5](./02-数据模型.md) `agent_task` / `agent_runtime_config` 表.

---

## 6. 任务队列设计

### 6.1 表结构 ✅

见 [02-数据模型 §2.5](./02-数据模型.md) `agent_task` 表.

### 6.2 任务类型 ✅

| task_type | payload | executor | 状态 |
|---|---|---|---|
| `ping` | `{}` | ping | ✅ |
| `agent_upgrade` | `{url, sha256, version}` | agent_upgrade | ✅ |
| `config_reload` | `{yaml, md5}` | config_reload | ✅ |
| `xray_provision_user` | `{uuid, email, totalBytes, expiryMillis, outboundTag, danteEndpoint, danteUser, dantePass}` | xray-api: adu + ado + adr | ✅ |
| `xray_remove_user` | `{email}` | xray-api: rmu + rmr + rmo | ✅ |
| `xray_update_outbound` | `{outboundTag, newDanteEndpoint, danteUser, dantePass}` | xray-api: rmo + ado (换 IP) | ✅ |
| `xray_update_user_quota` | `{email, newTotalBytes}` | xray-api: adu (重置流量) | ❌ |
| `xray_full_sync` | `{}` | xray-dump + diff + adu/rmu | ❌ Sprint 1 |
| `systemd_restart` | `{service}` | systemd-ctl: systemctl restart | ❌ |
| `shell_exec` | `{cmd, timeout}` | shell | ❌ |

### 6.3 任务流转 ✅

状态机由 `AgentTaskStatus` 枚举定义: `PENDING → PICKED → SUCCESS / FAILED`.

```
[backend 写任务]
  INSERT agent_task (server_id, type, payload, status=PENDING)

[agent 轮询 60s]
  GET /api/agent/tasks?server_id=X
  → 后端: SELECT WHERE status=PENDING ORDER BY created_at ASC LIMIT 10
          CAS UPDATE status=PICKED + picked_at=now
  → 返回任务列表

[agent 执行]
  对每个任务 dispatch 到对应 executor
  执行成功 → POST /task-result { task_id, status=SUCCESS, output }
  执行失败 → POST /task-result { task_id, status=FAILED, output=error_msg }

[backend 处理结果]
  status=SUCCESS → 标完成 (config_reload 还会回写 applied_at + applied_yaml_md5)
  status=FAILED → retry_count +1, 重置 status=PENDING (最多 3 次)
  retry_count=3 → 永久 FAILED + 告警 admin (告警 ❌ 未实施)

[超时兜底]  ❌
  调度任务每 5min 扫: WHERE status=PICKED AND picked_at < now - 10min
  → 重置 status=PENDING (agent 可能挂, 等下次起来重新拉)
```

---

## 7. 部署流程 (装机) ✅

### 7.1 流式 SSH 部署

`/admin/agent/install` (POST, 流式 SSE) → `AgentInstallScriptServiceImpl.installStreaming`:

```
1. resourceServerApi.validateExists(serverId)
2. resourceServerCredentialApi.requireByServerId(serverId)
3. agentInstallValidator.validateInstallPrerequisite(srv, reqVO)
4. 渲染 config.yml (admin 在 UI 填的 backend URL / timeouts / xrayBin / xrayApiPort 等)
5. SSH ad-hoc session (跑完即关) 跑 nook-agent.install.sh.tmpl:
   - 下载 binary (从 backend public URL curl)
   - 写盘 /home/nook-agent/{nook-agent, config.yml}
   - 写 systemd unit /etc/systemd/system/nook-agent.service
   - systemctl daemon-reload + enable --now
6. 等 1min 看 backend 是否收到第一次心跳 → server.runtime.last_heartbeat_at 不为 null
7. admin 手动切 lifecycle → READY → LIVE
```

详见 [代码: AgentInstallScriptServiceImpl.java](nook-module-agent/nook-module-agent-server/src/main/java/com/nook/biz/agent/service/impl/AgentInstallScriptServiceImpl.java).

### 7.2 systemd unit

```ini
[Unit]
Description=Nook Agent
After=network.target

[Service]
Type=simple
ExecStart=/home/nook-agent/nook-agent -c /home/nook-agent/config.yml
Restart=always
RestartSec=10s
User=root

[Install]
WantedBy=multi-user.target
```

`RestartSec=10s` 给自杀重启留出窗口 (`config_reload` / `agent_upgrade` executor 跑完会 `os.Exit(0)`).

### 7.3 Agent token 鉴权

- 装机时 backend 生成 `resource_server.agent_token` = `SHA256(UUID + UUID)` (64 char hex), 一次性写入
- yaml 模板里 `backend.agent_token: ${token}` 替换
- agent 调任何 backend 接口必带 `X-Agent-Token` header
- backend `AgentAuthService.verifyAndGetServerId(token)` 查表反查 server_id
- token 泄露只影响该 server, 不波及其他

---

## 8. Runtime Config 同步 (config_reload task) ✅

> 历史: 第一版 (C-1 ~ C-7) 上了三层 fallback + deep merge + 版本号机制, 实施过程中 effectiveVersion 公式翻车被砍. 重写成下面的简化版 (D 系列) — 用任务队列做配置同步, 跟现有 `agent_upgrade` / `truncate_log` 走同一通路.

### 8.1 核心想法

**Admin 改完 yaml → 后台派一个 task → agent 30s 内拉到 → 写盘 + 自重启**.

不需要新通信通道, 不需要分层配置, 不需要乐观锁, 不需要订阅 channel.

### 8.2 同步状态判定

基于 [02-数据模型 §2.5](./02-数据模型.md) `agent_runtime_config` 表:

- `applied_yaml_md5 == md5(config_yaml)` → **SYNCED** ✅
- 行不存在 → **NEVER_CONFIGURED** ⚪
- 其他 → **PENDING** ⏳

### 8.3 数据流

```
Admin UI                  Backend                          Agent                        systemd
  │
  │ textarea 改 yaml
  ├──► PUT /admin/agent-runtime-config/{serverId} {yaml}
  │                         │
  │                         │ AgentRuntimeConfigService.save:
  │                         │   1. yaml 语法校验 (snakeyaml)
  │                         │   2. UPSERT agent_runtime_config (yaml + updated_at)
  │                         │   3. INSERT agent_task (type=config_reload, payload={yaml, md5})
  │                         │      → status=PENDING
  │ ◀─── { data: taskId } ──┤
  │
  │ UI ⏳ 待应用
  │
  │                  ........... 等 ≤ 60s ...........
  │                                                                                    │
  │                  ◀── GET /api/agent/tasks ────────── 任务轮询 (poller 60s) ───────│
  │                  │ markPicked (CAS)                                                │
  │                  ├──► [config_reload task] ─────────────►│                         │
  │                                                          │ ConfigReloadExecutor:   │
  │                                                          │   • md5 校验 payload    │
  │                                                          │   • 原子写 config.yml   │
  │                                                          │   • go func(){          │
  │                                                          │       sleep 1s          │
  │                                                          │       os.Exit(0)        │
  │                                                          │     }()                 │
  │                  ◀── POST /api/agent/task-result ───────│                         │
  │                       SUCCESS + {md5, bytes, configPath}                          │
  │                  │ AgentReportServiceImpl.receiveTaskResult:                       │
  │                  │   • markResult SUCCESS                                          │
  │                  │   • 检 task_type == config_reload                              │
  │                  │   → AgentRuntimeConfigService.onConfigReloadSuccess(md5)       │
  │                  │     更新 applied_at + applied_yaml_md5                          │
  │                                                          × agent 进程退出 ────────►│
  │                                                                                    │
  │                                                          ◀──── RestartSec=10s ────┤
  │                                                          agent 新进程启动        │
  │                                                          (读 /home/nook-agent/config.yml)
  │                                                                                    │
  │                  ◀── POST /api/agent/heartbeat ──── 新进程第一次心跳              │
  │ UI 刷新看 ✅ 已同步
```

### 8.4 端点

| Method | Path | 谁调 | 干啥 |
|---|---|---|---|
| GET | `/admin/agent-runtime-config/{serverId}` | Admin | 查当前 yaml + 同步状态 |
| PUT | `/admin/agent-runtime-config/{serverId}` | Admin | 保存 yaml + 派 task; 返 taskId |
| GET | `/admin/agent/page` | Admin | 列表含 `configSyncState` |

**Agent 端**: 走现有 `GET /api/agent/tasks` + `POST /api/agent/task-result`, **无新端点**.

### 8.5 边角

- **校验**: backend 仅 yaml 语法校验 (snakeyaml parse), 不校字段; 字段含义由 agent 处理 (避免 backend ↔ agent 字段双向耦合)
- **MD5 安全**: payload 附 md5, agent 写盘前校验, 防 DB / 传输破坏
- **agent 失败**: task result_payload 含错误信息, status=FAILED, applied_at 不更新, UI 持续 PENDING
- **agent 离线**: task 留 PENDING, agent 上线自动拉
- **多次连改**: admin 连续保存 N 次 → 派 N 个 task; agent 串行处理; 中间版本会被应用但很快被覆盖; 最终一致 (最后一次 ack 写 applied_md5)
- **xray/socks5 不受影响**: agent 进程退出/拉起 ≈ 1-2 秒, xray/socks5 独立进程, 流量不中断

---

## 9. 安全设计

- **Token 鉴权** ✅: 每 server 一个 64 char SHA256 hex token, backend 反查 server_id
- **任务限流** ⚠️: shell executor 白名单路径 + max_runtime; xray-api executor 只能调指定 api 接口 (xray executor 已实施)
- **HTTPS only** ✅: 所有 API 必须 TLS, 拒绝 HTTP
- **配置变更审计** ✅: agent 收 config_reload task 上报 md5, backend 比对
- **任务幂等** ✅: 所有任务可重复执行不出错 (e.g., add user 重复 = 已存在, 不报错)

---

## 10. SSH 模式 vs Agent 模式对比

| 维度 | 旧 SSH 模式 | Agent 模式 (v3) |
|---|---|---|
| 后端 SSH 连接 | 维护到 N 台 server 的连接池 | 仅首次装机 |
| 数据采集 | 后端调度 SSH 拉 | Agent 主动 POST |
| 配置变更 | 后端 SSH push (实时) | 任务队列 + 轮询 (30s-1min 延迟) |
| 网络 | server 必须开 SSH 22 给 backend | server 主动出站 HTTPS |
| NAT 后服务器 | 麻烦 (要打洞) | 天然支持 |
| 后端横向扩展 | SSH 凭据分发问题 | HTTP API 加 LB |
| 故障检测 | 5-10min SSH 探活 | 1-3min 心跳超时 |
| Admin 远程命令 | SSH exec | task 派发 (shell executor) |
| 单 server 资源占用 | 0 (被动) | 5-15MB RAM (常驻 agent) |
| 配置变更实时性 | 几秒 | 30s-1min |

---

## 11. 起步阶段 vs 未来扩展

### 11.1 Sprint 0 已落地 (最小可用) ✅

- **Collectors**: heartbeat / vnstat / xray-stats
- **Executors**: ping / xray-api (adu/rmu/ado/rmo) / config_reload / agent_upgrade
- **任务类型**: xray_provision_user / xray_remove_user / xray_update_outbound / config_reload / agent_upgrade / ping
- **模板渲染 + 装机推送**: 流式 SSE
- **admin UI**: 服务器列表 / Agent 装机对话框 / runtime-config 编辑 / 任务历史

### 11.2 Sprint 1+ 扩展

- `xray_full_sync` task: 远端 xray dump + diff + 自动修复 (替代之前 SSH `XrayClientReconcilerJob`)
- agent 任务超时兜底 Job (PICKED > 10min 重置 PENDING)
- `xray_update_user_quota` task: 续费时重置流量基线

### 11.3 Sprint 5+ 远期

- 配置热更新 (SIGHUP reload, 不重启进程)
- 更多 executor (file-write / file-read / shell admin 远程命令)
- xray-dump 配置对账 + 自动修复
- WebSocket / gRPC stream 替代轮询 (实时性更高)
- Agent 灰度升级 (按 server tag / region 分批)
- agent 完整自检 (启动后跑 health probe 验证新配置可用, 失败回滚)

---

## 12. 未来不在范围

- 配置历史 + 回滚 — 当前是整段 yaml 覆盖
- yaml 字段表单化 (tab 分类)
- monaco-editor 语法高亮
- 全局任务流水可视化 (能看到所有 server 的 task 历史)
