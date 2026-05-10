# Nook 部署脚本

> 仅支持 Ubuntu 22.04+, 不向后兼容更老系统。

## 当前部署策略

| 角色 | 部署方式 | 入口 |
|-|-|-|
| **线路服务器 (line)** | nook 管理端 UI 模块化部署 | 资源 → 服务器 → 一键部署 |
| **落地节点 (socks5)** | nook 管理端 UI 一键部署 OR 手动跑本目录脚本 | 资源 → IP 池 → 一键部署 |

**线路服务器不再有手动安装脚本** — 走 nook 管理端的模块化部署 (xray + slot 池 + 可选模块勾选), 详见 [`docs/演进路线.md`](../docs/演进路线.md) 第 3.4.5 节。所有装机逻辑都在 [`nook-biz-module/nook-biz-node/src/main/resources/scripts/modules/`](../nook-biz-module/nook-biz-node/src/main/resources/scripts/modules/) 下的 7 个 `.sh.tmpl` 模块, nook 后端按勾选拼接后通过 SSH 上传执行。

## 落地节点手动部署 (本目录)

`install-socks5-landing.sh` 是落地节点的 SOCKS5 服务装机脚本, **可手动跑也可由 nook 管理端调用** (后端从 [`install-socks5-landing.sh.tmpl`](../nook-biz-module/nook-biz-node/src/main/resources/scripts/install-socks5-landing.sh.tmpl) 模板渲染下发, 行为一致)。

### 选 VPS

挑**便宜、IP 干净、有想要地区**的 VPS (美国 ISP IP / 日本住宅 IP / 香港等)。每台 VPS 跑一个 SOCKS5 server, 给一个独享出口 IP。

### 跑脚本

```bash
ssh root@<landing-server-ip>

# 拉脚本
wget https://raw.githubusercontent.com/<你的仓库>/main/scripts/install-socks5-landing.sh
chmod +x install-socks5-landing.sh

# 默认参数 (随机生成用户名密码)
sudo ./install-socks5-landing.sh

# 自定义
sudo SOCKS_PORT=1080 SOCKS_USER=user1 SOCKS_PASS=pass1 \
     ALLOW_FROM=1.2.3.4/32 INSTALL_UFW=true \
     ./install-socks5-landing.sh
```

### 可调环境变量

| 变量 | 默认 | 说明 |
|-|-|-|
| `SOCKS_PORT` | `1080` | SOCKS5 监听端口 |
| `SOCKS_USER` | 随机 | SOCKS5 鉴权用户名 |
| `SOCKS_PASS` | 随机 | SOCKS5 鉴权密码 |
| `ALLOW_FROM` | `0.0.0.0/0` | UFW 允许来源 CIDR (建议填线路服务器 IP) |
| `INSTALL_UFW` | `false` | 是否装 UFW + 防火墙规则 |

## 推荐部署流程

```
1. nook 管理端注册线路服务器 (录入 SSH 凭据)
2. 一键部署 → 模块化装 xray + 50 slot placeholder
3. 跑落地节点 install-socks5-landing.sh (手动 OR nook 管理端)
4. nook 管理端 IP 池录入落地 IP + SOCKS5 凭据
5. 给客户开通: provision client → 自动选 server slot + 绑定落地 IP
```

详细演进路线见 [`docs/演进路线.md`](../docs/演进路线.md)。
