# 05-Agent 架构

> v3 Agent 设计 — Go 实现 + 配置驱动 + 任务队列 + 部署流程. Go 开发者必读, 运维参考.

---

## 十五、Agent 架构 (v3 新增)

### 15.1 设计目标

把现在后端 SSH 主动拉数据 / 推配置的模式, 反转为**服务器主动 push + 拉任务**.

- **数据流方向反转**: 数据从 server → backend (HTTPS POST), 不再后端 SSH 拉
- **配置变更走任务队列**: backend 写任务, agent 轮询拉 + 执行 + 回报
- **配置驱动**: agent 行为完全由后端推送的 yaml 决定, 加新能力 = 加模块 + 改模板
- **单一二进制**: 一个 nook-agent 二进制部署到所有 server (线路机 / 落地 IP 都用), 行为靠配置区分

### 15.2 实现语言: Go

**选择 Go 的理由**:
- Agent 职责简单 (采集数据 push + 拉任务 pull + 调本地 xray API), 不涉及复杂业务逻辑
- 二进制 5-10MB, 1C1G 占用 5-15MB, 极轻量
- 单二进制部署, 无 runtime 依赖
- 启动快 (< 100ms), 重启代价低
- 后端 Java 负责接口校验 / 业务逻辑, agent 只做执行器

**Java 团队跟 Go agent 协作**:
- Backend (Java) 定义 HTTP API 契约 (OpenAPI / Swagger)
- Agent (Go) 实现客户端, 按契约调用
- DTO 不共享代码 (重新写一份 Go struct, 工作量小, JSON tag 对齐即可)
- 后端 Java 完整校验请求 (字段 / 范围 / 鉴权), agent 只发数据

**学习成本**:
- Go 是简单语言 (相比 Java), 1-2 周能上手
- Agent 代码量预估 1500-2500 行 Go (含 collectors + executors + http client)
- 一次写完后维护频率低 (大部分变更在 backend 侧)

### 15.3 Agent 内部结构

```
nook-agent (Go, 单二进制 ~10MB)
├─ 内核
│   ├─ config loader (读 /etc/nook-agent/config.yml)
│   ├─ scheduler (按 interval 跑 collectors)
│   ├─ poller (定时 GET task queue)
│   ├─ reporter (HTTPS POST 数据)
│   └─ executor dispatcher
│
├─ Collectors (按 config 启用)
│   ├─ vnstat       — NIC 流量采样, 算周期已用
│   ├─ xray-stats   — xray statsquery (per user up/down)
│   ├─ xray-dump    — xray api 拉当前用户列表 (配置对账用)
│   ├─ dante-stats  — dante 连接数 / 流量
│   ├─ systemd      — systemd 服务状态 (xray/dante)
│   ├─ procinfo     — CPU/Mem/Disk
│   └─ heartbeat    — 1min 一次心跳
│
└─ Executors (按 config 启用)
    ├─ xray-api     — xray api stdin 命令 (adu/rmu/adr/rmr/ado/rmo)
    ├─ systemd-ctl  — systemctl start/stop/restart (白名单)
    ├─ shell        — 任意 shell (admin 远程命令, 白名单)
    ├─ file-write   — 写本地文件 (配置变更)
    └─ file-read    — 读文件 (日志 / 配置查看)
```

### 15.4 配置模板 (backend 渲染)

#### 模板 A: 纯线路机

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: srv-jp-01
  api_token: ${generated_token}  # 32 char, 每 server 独立

collectors:
  - type: heartbeat
    interval: 1m
  
  - type: vnstat
    interval: 5m
    interface: eth0
  
  - type: xray-stats
    interval: 5m
    xray_api: 127.0.0.1:10085
    pattern: "user>>>email>>>traffic"
  
  - type: xray-dump
    interval: 10m
    xray_api: 127.0.0.1:10085
  
  - type: systemd
    interval: 1m
    services: [xray]

executors:
  - type: xray-api
    xray_api: 127.0.0.1:10085
  - type: systemd-ctl
    allowed_services: [xray]
  - type: shell
    enabled: true
    max_runtime: 30s
    allowed_paths: [/var/log, /etc/xray]
```

#### 模板 B: 纯落地 IP

```yaml
backend:
  api_url: https://nook-api.example.com
  server_id: ip-jp-1
  api_token: ${generated_token}

collectors:
  - type: heartbeat
    interval: 1m
  - type: vnstat
    interval: 5m
    interface: eth0
  - type: dante-stats
    interval: 5m
  - type: systemd
    interval: 1m
    services: [danted]

executors:
  - type: systemd-ctl
    allowed_services: [danted]
  - type: shell
    enabled: true
    max_runtime: 30s
```

### 15.5 Backend HTTP API

```
[Agent → Backend POST]
  POST /api/agent/heartbeat
  POST /api/agent/nic-traffic
  POST /api/agent/xray-traffic
  POST /api/agent/xray-config-dump  (对账)
  POST /api/agent/dante-stats
  POST /api/agent/sysinfo
  POST /api/agent/task-result

[Agent → Backend GET]
  GET  /api/agent/tasks?server_id=X     (轮询任务)
  GET  /api/agent/config-version        (检查配置是否更新)

[认证] Bearer token (api_token), 每 server 一份, 后端校验
```

### 15.6 任务队列设计

```sql
CREATE TABLE agent_task (
  id              CHAR(32)  PRIMARY KEY,
  server_id       CHAR(32)  NOT NULL,
  task_type       VARCHAR(64) NOT NULL,    -- 'xray_add_user' / 'xray_rmu' / 'shell_exec' / ...
  task_payload    JSON NOT NULL,            -- 任务参数 (uuid, email, totalBytes, ...)
  status          ENUM('PENDING','PICKED','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
  picked_at       DATETIME,
  result_payload  JSON,                    -- agent 回报结果
  retry_count     INT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL,
  updated_at      DATETIME NOT NULL,
  INDEX idx_server_pending (server_id, status, created_at)
);
```

**任务类型** (起步):

| task_type | payload | executor |
|---|---|---|
| `xray_provision_user` | { uuid, email, totalBytes, expiryMillis, outboundTag, danteEndpoint, danteUser, dantePass } | xray-api: adu + ado + adr |
| `xray_remove_user` | { email } | xray-api: rmu + rmr + rmo |
| `xray_update_user_quota` | { email, newTotalBytes } | xray-api: adu (重置流量) |
| `xray_update_outbound` | { outboundTag, newDanteEndpoint, danteUser, dantePass } | xray-api: rmo + ado (换 IP) |
| `systemd_restart` | { service } | systemd-ctl: systemctl restart |
| `shell_exec` | { cmd, timeout } | shell |
| `agent_config_reload` | {} | 内部: 拉新 yaml + SIGHUP |

### 15.7 任务流转 (含失败重试)

```
[backend 写任务]
  INSERT agent_task (server_id, type, payload, status=PENDING)

[agent 轮询 30s]
  GET /api/agent/tasks?server_id=X
  → 后端: SELECT WHERE status=PENDING ORDER BY created_at ASC LIMIT 10
          UPDATE 标 status=PICKED + picked_at=now
  → 返回任务列表

[agent 执行]
  对每个任务 dispatch 到对应 executor
  执行成功 → POST /task-result { task_id, status=SUCCESS, output }
  执行失败 → POST /task-result { task_id, status=FAILED, output=error_msg }

[backend 处理结果]
  status=SUCCESS → 标完成
  status=FAILED → retry_count +1, 重置 status=PENDING (最多 3 次)
  retry_count=3 → 永久 FAILED + 告警 admin

[超时兜底]
  调度任务每 5min 扫: WHERE status=PICKED AND picked_at < now - 10min
  → 重置 status=PENDING (agent 可能挂了, 等下次它起来重新拉)
```

### 15.8 部署流程 (装机)

```bash
# ServerProvisioner SSH 进新机器:
1. apt install vnstat (+ xray / dante 按角色)
2. 渲染 nook-agent.yml 配置文件
3. scp:
   - /usr/local/bin/nook-agent       (二进制)
   - /etc/nook-agent/config.yml      (配置)
   - /etc/systemd/system/nook-agent.service
4. systemctl enable --now nook-agent
5. 测试: 等 1min 看 backend 是否收到第一次心跳
6. 收到心跳 → server.lifecycle_state = READY
7. admin 在后台关联资源 + 点 "上线" → LIVE
```

systemd unit:
```ini
[Unit]
Description=Nook Agent
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/nook-agent -c /etc/nook-agent/config.yml
Restart=always
RestartSec=10s
User=nook-agent

[Install]
WantedBy=multi-user.target
```

### 15.8 安全设计

- **Token 鉴权**: 每 server 一个 32 char 随机 token, 后端校验, token 泄露只影响该 server
- **任务限流**: shell executor 白名单路径 + max_runtime; xray-api executor 只能调指定 api 接口
- **HTTPS only**: 所有 API 必须 TLS, 拒绝 HTTP
- **配置变更审计**: agent 收到 config-reload 任务时上报 sha256, backend 比对
- **任务幂等**: 所有任务可重复执行不出错 (e.g., add user 重复 = 已存在, 不报错)

### 15.9 起步阶段 vs 未来扩展

**Sprint 1 落地 (最小可用)**:
- collectors: heartbeat / vnstat / xray-stats / systemd
- executors: xray-api / systemd-ctl
- 任务类型: xray_provision_user / xray_remove_user / xray_update_user_quota / xray_update_outbound
- 模板渲染 + 装机推送

**Sprint 5+ 扩展**:
- Agent 自动升级 (版本协商 + 灰度)
- 配置热更新 (SIGHUP reload)
- 更多 executor (file-write / file-read / shell admin 远程命令)
- xray-dump 配置对账 + 自动修复
- WebSocket / gRPC stream 替代轮询 (实时性更高)

### 15.10 跟现有 SSH 模式的对比

| 维度 | SSH 模式 (现) | Agent 模式 (v3) |
|---|---|---|
| 后端 SSH 连接 | 维护到 N 台 server 的连接池 | 仅首次装机 |
| 数据采集 | 后端调度 SSH 拉 | Agent 主动 POST |
| 配置变更 | 后端 SSH push (实时) | 任务队列 + 轮询 (30s-1min 延迟) |
| 网络 | server 必须开 SSH 22 给 backend | server 主动出站 HTTPS |
| NAT 后服务器 | 麻烦 (要打洞) | 天然支持 |
| 后端横向扩展 | SSH 凭据分发问题 | HTTP API 加 LB |
| 故障检测 | 5-10min SSH 探活 | 1-3min 心跳超时 |
| Admin 远程命令 | SSH exec | task 派发 (shell executor) |
| 单 server 资源占用 | 0 (被动) | 5-10MB RAM (常驻 agent) |
| 配置变更实时性 | 几秒 | 30s-1min |
