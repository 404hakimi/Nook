# nook-agent

Nook v3 服务器端常驻进程; 主动 push 心跳 + NIC 流量给 backend, 轮询任务队列拉派工.

## 模块

```
nook-agent/
├── main.go                      启动 + 三个 goroutine 调度 (heartbeat / nic / poller)
├── internal/
│   ├── config/                  yaml 配置加载 + 默认值 + 校验
│   ├── client/                  backend HTTP 客户端 (含 X-Agent-Token 鉴权)
│   ├── heartbeat/               每 1min POST /api/agent/heartbeat
│   ├── nic/                     每 5min 调 vnstat → POST /api/agent/nic-traffic
│   ├── poller/                  每 30s GET /api/agent/tasks → 派 executor → 回报结果
│   └── executor/                task_type → Handler 注册表
├── config.yml.sample            部署模板
└── nook-agent.service           systemd unit
```

## 构建 (Linux amd64)

```bash
# 在 Linux 主机上 (Windows 没装 Go 时用 SSH 到目标服务器)
go build -ldflags "-s -w" -o nook-agent .
```

## 部署

```bash
# 1. SCP 二进制 + 配置
scp nook-agent root@server:/usr/local/bin/
scp nook-agent.service root@server:/etc/systemd/system/
# 渲染 config.yml (api_url + api_token 装机时由 backend 给) → scp 到 /etc/nook-agent/

# 2. 启动
ssh root@server "systemctl daemon-reload && systemctl enable --now nook-agent"

# 3. 看日志
ssh root@server "journalctl -u nook-agent -f"
```

## 起步阶段已实现

- ✅ 心跳上报 (1min)
- ✅ NIC 流量上报 (5min, vnstat)
- ✅ 任务队列轮询 (30s)
- ✅ ping 测试 task_type (executor 冒烟测试)

## Sprint 1+ 待补

- xray-api executor (调本地 xray API 增删用户 / outbound)
- xray-stats collector (每 5min 调 xray statsquery POST /api/agent/xray-traffic)
- systemd-ctl executor (systemctl restart 等白名单命令)
- 配置热更新 (SIGHUP reload)
- 自动升级 (检测 backend 推送的版本号)
