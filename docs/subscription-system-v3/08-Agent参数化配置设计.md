# 08 · Agent 运行时配置 — 简化版 (D 设计)

> **状态**: ✅ 已实施 (2026-05-20).
>
> **历史**: 第一版 (C-1 ~ C-7) 上了三层 fallback + deep merge + 版本号机制, 实施过程中 effectiveVersion 公式翻车被砍。重写成本文的简化版 — 用任务队列做配置同步, 跟现有 `agent_upgrade` / `truncate_log` 走同一通路。

---

## 核心想法

**Admin 改完 yaml → 后台派一个 task → agent 30 秒内拉到 → 写盘 + 自重启**。

不需要新通信通道, 不需要分层配置, 不需要乐观锁, 不需要订阅 channel。

---

## 1. 数据 (单表)

```sql
CREATE TABLE agent_runtime_config (
  server_id        CHAR(32) PRIMARY KEY,           -- FK → resource_server.id
  config_yaml      MEDIUMTEXT NOT NULL,            -- admin 编辑的整段 yaml
  updated_at       DATETIME NOT NULL,              -- admin 改的时间
  updated_by       VARCHAR(64) NOT NULL,
  applied_at       DATETIME DEFAULT NULL,          -- agent 应用时间 (任务 SUCCESS 后写)
  applied_yaml_md5 CHAR(32) DEFAULT NULL           -- agent 应用版本的 md5
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**同步状态判定**:
- `applied_yaml_md5 == md5(config_yaml)` → **SYNCED** ✅
- 没行 → **NEVER_CONFIGURED** ⚪
- 其他 → **PENDING** ⏳

---

## 2. 数据流

```
Admin UI                Backend                              Agent (running)         systemd
  │
  │ ①textarea 改 yaml
  ├──► PUT /admin/agent-runtime-config/{serverId} {yaml}
  │                       │
  │                       │ AgentRuntimeConfigService.save:
  │                       │   1. yaml 语法校验 (snakeyaml)
  │                       │   2. UPSERT agent_runtime_config (yaml + updated_at)
  │                       │   3. INSERT agent_task (type=config_reload, payload={yaml, md5})
  │                       │      → status=PENDING
  │ ◀─── { data: taskId } ─┤
  │
  │ ②UI 显示 ⏳ 待应用
  │
  │                  ........... 等 ≤ 30s ...........
  │                                                                                  │
  │                  ◀── GET /api/agent/tasks ────────── 任务轮询 (poller, 30s)      │
  │                  │ markPicked                                                     │
  │                  ├──► [config_reload task] ─────────────►│                       │
  │                                                          │ ConfigReloadExecutor: │
  │                                                          │   • md5 校验 payload  │
  │                                                          │   • 原子写 /etc/nook-agent/config.yml
  │                                                          │   • go func(){ sleep 1s; os.Exit(0) }
  │                  ◀── POST /api/agent/task-result ───────│                       │
  │                       SUCCESS + {md5, bytes, configPath}                         │
  │                  │ AgentReportService.receiveTaskResult:                          │
  │                  │   • markResult SUCCESS                                          │
  │                  │   • 检测 task_type == config_reload                            │
  │                  │   • → AgentRuntimeConfigService.onConfigReloadSuccess(md5)    │
  │                  │     更新 applied_at + applied_yaml_md5                         │
  │                                                          × agent 进程退出 ───────►│
  │                                                                                   │
  │                                                          ◀──── RestartSec=10s ───┤
  │                                                          agent 新进程启动        │
  │                                                          (读 /etc/nook-agent/config.yml)
  │                                                                                   │
  │                  ◀── POST /api/agent/heartbeat ──── 新进程第一次心跳              │
  │ ③UI 刷新看 ✅ 已同步
```

---

## 3. 实现清单

### DB
- `agent_runtime_config` (1 张表, §1 schema)

### Backend (Java, ~6 文件)
- `AgentRuntimeConfigDO` / `Mapper`
- `AgentRuntimeConfigService` + `Impl` (save / get / onConfigReloadSuccess)
- `AdminAgentRuntimeConfigController` (GET / PUT, 2 个端点)
- `AgentReportServiceImpl.receiveTaskResult` 加 config_reload 后处理
- `AdminAgentServiceImpl.list()` 加 configSyncState 列
- `AdminAgentListItemRespVO` 加 `configSyncState` 字段

### Agent (Go, 1 新文件)
- `internal/executor/config_reload_executor.go` (50 行) — 写盘 + 自杀
- `main.go` 注册一行: `executor.NewConfigReloadExecutor(*configPath).Register(exec)`
- 其他 collector 完全不动 (心跳 / NIC / 任务轮询签名都没改)

### Frontend (1 新文件 + 2 改)
- `src/api/agent/runtime-config.ts` — 2 个函数 (get/save)
- `src/views/agent/ConfigEditDialog.vue` — textarea + 保存按钮 + 同步状态 badge
- `src/views/agent/AgentList.vue` — 加 "配置同步" 列 + "改配置" 操作按钮
- `src/api/agent/agent.ts` — `AgentListItem` 加 `configSyncState` + label/tag-type 表

---

## 4. 端点

| Method | Path | 谁调 | 干啥 |
|---|---|---|---|
| GET | `/admin/agent-runtime-config/{serverId}` | Admin | 查当前 yaml + 同步状态 |
| PUT | `/admin/agent-runtime-config/{serverId}` | Admin | 保存 yaml + 派 task; 返 taskId |
| GET | `/admin/agent/list` | Admin | 列表含 `configSyncState` |

**Agent 端**: 走现有 `GET /api/agent/tasks` + `POST /api/agent/task-result`, **无新端点**。

---

## 5. UX

```
Agent 列表:
┌────────────────────────────────────────────────────────────────────────────────────┐
│ 服务器       Agent版本   在线状态   心跳延迟   上次心跳         配置同步    操作    │
│ ─────────                                                                          │
│ fra-test    0.5.0       ● 在线     18s        2026-05-20 16:54  ✅ 已同步           │
│                                                                  [改配置][升级][清日志]│
└────────────────────────────────────────────────────────────────────────────────────┘

点 "改配置" → 弹窗:
┌────────────────────────────────────────────────────────────────────────────────────┐
│ 运行时配置: fra-test                                                       [×]    │
│ ────────────────────────────────────────────────────────────────────────────────── │
│ 💡 保存后, backend 派 config_reload task; agent 在 30s 内拉到, 写盘 + 自重启.       │
│                                                                                    │
│ ✅ 已同步   上次保存: 2026-05-20 16:54 by 90b8f3...  · agent 应用: 2026-05-20 16:54 │
│                                                                                    │
│ ┌──────────────────────────────────────────────────────────────────────────────┐│
│ │ backend:                                                                     ││
│ │   api_url: http://mb66aadc.natappfree.cc                                     ││
│ │   timeout_seconds: 30                                                        ││
│ │ heartbeat:                                                                   ││
│ │   interval_seconds: 60                                                       ││
│ │ ...                                                                          ││
│ └──────────────────────────────────────────────────────────────────────────────┘│
│                                                                                    │
│                                                       [取消]  [💾 保存 + 派发]     │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. 边角

- **校验**: backend 仅做 yaml 语法校验 (snakeyaml parse), 不校字段; 字段含义由 agent 处理 (避免 backend 跟 agent 字段双向耦合)
- **MD5 安全**: payload 里附 md5, agent 写盘前校验, 防 payload 在 DB / 传输被破坏
- **agent 失败**: task `result_payload` 含错误信息, status=FAILED, applied_at 不更新, UI 持续显示 PENDING (admin 可看 task 日志排查)
- **agent 离线**: task 留 PENDING, agent 上线自动拉
- **多次连改**: admin 连续保存 N 次 → 派 N 个 task; agent 串行处理 (poller batch_size 限制); 中间版本 yaml 也会被应用但很快被覆盖; 不影响最终一致性 (最后一次 ack 写 applied_md5)
- **xray/socks5 不受影响**: agent 进程退出/拉起 ≈ 1-2 秒, 期间 xray 和 socks5 是独立进程, 流量不中断

---

## 7. 不在范围 (未来)

- 配置历史 + 回滚
- yaml 字段表单化 (tab 分类)
- monaco-editor 语法高亮
- 全局任务流水可视化 (能看到所有 server 的 task 历史)
- agent 完整自检 (启动后跑 health probe 验证新配置可用, 失败回滚)

---

## 8. 跟最初想法的差异 (反思)

| | 最初设计 (C 系列) | 现在 (D 系列) |
|---|---|---|
| 表数量 | 3 (global + override + apply_state) | 1 |
| Java 文件 | 16 | 6 |
| Go 新文件 | 2 (effective.go, store.go) | 1 (config_reload_executor.go) |
| 概念 | 三层 fallback + deep merge + 单调 version + ack endpoint | yaml + md5 |
| 通信新通道 | 心跳响应捎带 (改协议) | 复用 task 队列 (零新通道) |
| 实施过程 bug | 1 (effectiveVersion = max 公式回退) | 0 |

教训记在 `~/.claude/projects/.../memory/design-simplicity.md` — 个位数 server 的 SaaS 不要上分层 / 合并 / 锁。
