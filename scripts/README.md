# Nook 部署脚本

两个一键脚本，对应 Nook 架构里两类服务器。**仅支持 Ubuntu 22.04+**，不打算往后兼容更老系统。

## 角色与拓扑

```
[客户端 v2rayN]
       │
       │ vmess
       ▼
[线路服务器 line-xxx]   <-- install-line-server.sh
   ├─ Xray 内核 (官方版本, 不带 3x-ui)
   ├─ vmess 入站, port=$VMESS_PORT, clients=[] (空, 由 nook 通过 gRPC 加)
   └─ Xray gRPC API on 127.0.0.1:$XRAY_API_PORT
       │
       │ nook 通过 SSH 隧道 → 调 HandlerService.AlterInbound 加 user
       │ nook 通过 SSH 隧道 → 调 HandlerService.AddOutbound 加 socks5 outbound
       │ nook 通过 SSH 隧道 → 调 RoutingService.AddRule 按 email 分流
       ▼
[落地节点 socks5-xxx]   <-- install-socks5-landing.sh
   └─ 3proxy SOCKS5 server
       │
       ▼
   Internet (客户最终看到的 IP)
```

| 角色 | 脚本 | 装的东西 |
|------|------|---------|
| 线路服务器 | `install-line-server.sh` | 纯 Xray + nook 标配 xray.json + UFW |
| 落地节点 | `install-socks5-landing.sh` | 3proxy + UFW |

**注意：脚本不装 3x-ui**。Xray 由 nook 完全管控（client / outbound / routing 全在 gRPC 内存里）。如果你想要监控 UI，建议装 [Netdata](https://www.netdata.cloud/)（只读监控、不动 Xray）；或单独装 3x-ui 但要知悉它会覆盖 Xray 配置（需手动合并 `api`/`stats`/`policy` 段）。

## 工作流：从买 VPS 到能卖货

### Step 1. 装一台线路服务器

挑一个**网络好、对国内延迟低**的 VPS（日本、香港、新加坡），用于做用户的接入点。

```bash
ssh root@<line-server-ip>

# 拉脚本
wget https://raw.githubusercontent.com/<你的仓库>/main/scripts/install-line-server.sh
chmod +x install-line-server.sh

# 默认端口安装 (vmess 443, gRPC 62789)
sudo ./install-line-server.sh

# 自定义端口
sudo VMESS_PORT=2087 XRAY_API_PORT=51234 ./install-line-server.sh
```

可调环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `VMESS_PORT` | `443` | vmess 入站端口（公网开放） |
| `XRAY_API_PORT` | `62789` | Xray gRPC API 端口（仅 loopback） |
| `LOG_DIR` | `/var/log/xray` | Xray 日志目录 |

脚本干的事：
1. apt 装基础工具
2. 跑 [XTLS/Xray-install](https://github.com/XTLS/Xray-install) 官方脚本装 Xray binary + systemd
3. **覆盖** `/usr/local/etc/xray/config.json`（先备份原文件）成 nook 标配模板：含 `api/stats/policy/inbound api/inbound vmess/outbound api+direct/routing api`
4. `xray test` 验语法
5. UFW: 仅放 22 + vmess 端口
6. 启动 + 验证 gRPC 端口监听
7. 输出 nook 后台需要填的连接信息

### Step 2. 装 N 台落地节点

挑**便宜、IP 干净、有想要地区**的 VPS（美国 ISP IP、日本住宅 IP、香港等）。每台 VPS 都跑一个 SOCKS5 server。

```bash
ssh root@<landing-server-ip>

wget https://raw.githubusercontent.com/<你的仓库>/main/scripts/install-socks5-landing.sh
chmod +x install-socks5-landing.sh

# 推荐: 限制只接受线路服务器来源
sudo ALLOW_FROM=<line-server-public-ip>/32 ./install-socks5-landing.sh
```

装完会打 SOCKS5 用户名/密码/端口/出网 IP。**记下**，等会儿要进 nook 后台。

可调环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `SOCKS_PORT` | `1080` | SOCKS5 监听端口 |
| `SOCKS_USER` | `nookXXXXXX` 随机 | SOCKS5 用户名 |
| `SOCKS_PASS` | 随机 24 字符 | SOCKS5 密码 |
| `ALLOW_FROM` | `0.0.0.0/0` | UFW 限制源 IP；**生产强烈建议设成线路服务器 IP/32** |

### Step 3. 在 nook 后台录入

1. **服务器管理** → 新增：填线路服务器的 SSH + Xray gRPC 信息
   - `backendType` = `xray-grpc`
   - `xrayGrpcHost` = `127.0.0.1`
   - `xrayGrpcPort` = `62789`（脚本里设的那个）

2. **IP 池** → 新增：每台落地节点录一条
   - region：地区标签，如 `us-west-isp`
   - ipType：ISP / 家宽 / 机房
   - SOCKS5 host:port + user:pass

3. **测试连通性**：点服务器列表的"测速"按钮，nook 会通过 SSH 隧道连 Xray gRPC 测一次。

### Step 4. 跑业务流（CDK 生成 / 兑换 / 出配置）

见 nook 后台菜单"套餐与 CDK"、会员前台"兑换"流程（**待实现**）。

## 安全建议

### 线路服务器
- 禁用 root SSH 密码登录，**改密钥登录**
- UFW 只放 22 + vmess 端口
- 定期 `apt upgrade`
- gRPC 端口本身只 listen 127.0.0.1，**外人扫不到**；但若你本机被 RCE，gRPC 还是裸的——后续可加 token 鉴权

### 落地节点
- **必须**用 `ALLOW_FROM=<线路 IP>/32` 限制 UFW 源
- 否则 SOCKS5 端口会被全网扫到、被滥用刷走流量
- 3proxy 用户密码定期轮换（也可由 nook 自动轮换 — 后续支持）

## 卸载

### 线路服务器
```bash
# 用 Xray 官方卸载脚本
bash -c "$(curl -fsSL https://raw.githubusercontent.com/XTLS/Xray-install/main/install-release.sh)" @ remove --purge
ufw --force reset
```

### 落地节点
```bash
systemctl disable --now 3proxy
apt purge -y 3proxy
rm -f /etc/3proxy/3proxy.cfg
ufw --force reset
```

## 故障排查

### 线路服务器：Xray gRPC 端口没监听

```bash
ss -ltn | grep 62789                     # 是否监听
systemctl status xray
journalctl -u xray -n 50 --no-pager
xray test -c /usr/local/etc/xray/config.json   # 配置语法
```

### 线路服务器：nook 测速失败

```bash
# 在 nook 服务器上手动建 SSH 隧道试一下
ssh -L 62789:127.0.0.1:62789 -N root@<line-ip>

# 另开终端
nc -zv 127.0.0.1 62789      # 端口能通
```

### 落地节点：SOCKS5 通不了

```bash
systemctl status 3proxy
tail -f /var/log/3proxy/3proxy.log

# 本地自测
curl -v --socks5 user:pass@127.0.0.1:1080 https://ifconfig.me

# UFW 状态
ufw status verbose
```
