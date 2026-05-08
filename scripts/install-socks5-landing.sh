#!/usr/bin/env bash
#
# Nook 落地节点(SOCKS5 出口)一键安装脚本 — Ubuntu 22+
#
# 这台机器干啥：
#   - 跑 3proxy 提供 SOCKS5 服务
#   - 接受线路服务器的转发流量,从本机 IP 出网
#   - 客户最终看到的"IP" = 这台机器的公网 IP
#
# 用法:
#   sudo ./install-socks5-landing.sh
#
# 环境变量(可选):
#   SOCKS_PORT       默认 1080
#   SOCKS_USER       默认随机 8 字符
#   SOCKS_PASS       默认随机 24 字符
#   ALLOW_FROM       默认 0.0.0.0/0;
#                    安全建议: 设成"线路服务器的公网 IP/32" 限制只接受来自线路的连接
#

set -euo pipefail

# ===== 全局变量 =====
SOCKS_PORT="${SOCKS_PORT:-1080}"
SOCKS_USER="${SOCKS_USER:-nook$(tr -dc 'a-z0-9' </dev/urandom | head -c 6)}"
SOCKS_PASS="${SOCKS_PASS:-$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24)}"
ALLOW_FROM="${ALLOW_FROM:-0.0.0.0/0}"

# ===== 颜色 =====
# 仅在 TTY 上加颜色; 非 TTY (如 nook 远程调用) 走纯文本, 避免 ANSI 码污染流式日志
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    RED='\033[0;31m'
    NC='\033[0m'
else
    GREEN=''; YELLOW=''; RED=''; NC=''
fi

log()  { echo -e "${GREEN}[+]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[x]${NC} $*" >&2; }

# ===== 前置检查 =====

[[ $EUID -eq 0 ]] || { err "请以 root 运行 (sudo ./install-socks5-landing.sh)"; exit 1; }

if [[ ! -f /etc/os-release ]]; then
    err "无法识别系统,需要 Ubuntu 22+"; exit 1
fi
. /etc/os-release
if [[ "$ID" != "ubuntu" ]] || [[ "${VERSION_ID%.*}" -lt 22 ]]; then
    err "仅支持 Ubuntu 22.04+, 当前: $ID $VERSION_ID"; exit 1
fi

log "系统检查通过: Ubuntu $VERSION_ID"

# ===== Step 1. 装基础工具 =====

log "更新系统 + 装 3proxy..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl wget ufw 3proxy

# ===== Step 2. 写 3proxy 配置 =====
# 关键策略:
#   - users 里写明文密码; CL = cleartext password
#   - auth strong 强制密码认证
#   - allow USER 限制只允许这个 user 进
#   - socks -p<port> 启动 SOCKS5 监听
#   - nserver 8.8.8.8 设 DNS

CFG=/etc/3proxy/3proxy.cfg

log "写 3proxy 配置: $CFG"

cat > "$CFG" <<EOF
# nook SOCKS5 落地节点配置 (3proxy 自动生成)
# 用户: $SOCKS_USER
# 端口: $SOCKS_PORT

# ---- 系统 ----
daemon
pidfile /var/run/3proxy.pid
nserver 1.1.1.1
nserver 8.8.8.8
nscache 65536
log /var/log/3proxy/3proxy.log D
logformat "L%d-%m-%Y %H:%M:%S %z %N.%p %E %U %C:%c %R:%r %O %I %h %T"
rotate 7

# ---- 用户 ----
users $SOCKS_USER:CL:$SOCKS_PASS

# ---- 鉴权策略 ----
auth strong
allow $SOCKS_USER

# ---- SOCKS5 监听 ----
socks -p$SOCKS_PORT
EOF

# 日志目录
mkdir -p /var/log/3proxy
chown nobody:nogroup /var/log/3proxy 2>/dev/null || true

# ===== Step 3. systemd =====

# 默认 ubuntu 仓的 3proxy 已经带了 systemd unit (/lib/systemd/system/3proxy.service)
# 没有就手写一个

if [[ ! -f /lib/systemd/system/3proxy.service ]] && [[ ! -f /etc/systemd/system/3proxy.service ]]; then
    log "创建 3proxy systemd unit..."
    cat > /etc/systemd/system/3proxy.service <<'UNIT'
[Unit]
Description=3proxy SOCKS5 server (nook landing)
After=network.target

[Service]
Type=forking
ExecStart=/usr/bin/3proxy /etc/3proxy/3proxy.cfg
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT
    systemctl daemon-reload
fi

systemctl enable 3proxy >/dev/null 2>&1
systemctl restart 3proxy
sleep 1

if ! systemctl is-active --quiet 3proxy; then
    err "3proxy 启动失败 — journalctl -u 3proxy"
    exit 1
fi

# ===== Step 4. 防火墙 =====

log "配置防火墙 (允许来源: $ALLOW_FROM)..."
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp comment "SSH" >/dev/null
ufw allow from "$ALLOW_FROM" to any port "$SOCKS_PORT" proto tcp comment "SOCKS5 from line server" >/dev/null
ufw --force enable >/dev/null

# ===== Step 5. 自检 =====

log "自检: 通过本地 SOCKS5 探出网 IP..."
PUBLIC_IP_VIA_SOCKS=$(curl -s --max-time 10 \
    --socks5 "$SOCKS_USER:$SOCKS_PASS@127.0.0.1:$SOCKS_PORT" \
    https://ipinfo.io/ip 2>/dev/null || echo "TEST_FAILED")

if [[ "$PUBLIC_IP_VIA_SOCKS" == "TEST_FAILED" ]]; then
    warn "本地 SOCKS5 自检失败,但服务已起来; 检查 /var/log/3proxy/3proxy.log"
    PUBLIC_IP=$(curl -s4 --max-time 5 ifconfig.me || echo "<your-ip>")
else
    PUBLIC_IP="$PUBLIC_IP_VIA_SOCKS"
    log "✔ SOCKS5 自检通过, 出网 IP = $PUBLIC_IP"
fi

# ===== 最后输出 =====

cat <<EOF

${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}
${GREEN}                   SOCKS5 落地节点安装完成!${NC}
${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}

  SOCKS5 地址:    socks5://${PUBLIC_IP}:${SOCKS_PORT}
  用户:           ${SOCKS_USER}
  密码:           ${SOCKS_PASS}     ${YELLOW}<-- 记下,不会再显示${NC}
  允许来源:       ${ALLOW_FROM}
  出网 IP:        ${PUBLIC_IP}

${YELLOW}━━━━━━━━━━━━━━ 接下来 ━━━━━━━━━━━━━━${NC}

  1. 在 nook 后台 "IP 池" 录入这条出口:
     - region:           (按机器位置填, 如 us-west-isp)
     - ip_type:          ISP / 家宽 / 机房
     - ip_address:       ${PUBLIC_IP}
     - socks5_host:      ${PUBLIC_IP}
     - socks5_port:      ${SOCKS_PORT}
     - socks5_username:  ${SOCKS_USER}
     - socks5_password:  ${SOCKS_PASS}

  2. ${YELLOW}强烈建议:${NC} 用 ALLOW_FROM 限制只接受来自线路服务器的连接,
     防止 SOCKS5 被外人扫到滥刷你的流量:
     export ALLOW_FROM="<line-server-public-ip>/32"
     ./install-socks5-landing.sh

  3. 验证落地工作正常:
     curl -s --socks5 ${SOCKS_USER}:${SOCKS_PASS}@${PUBLIC_IP}:${SOCKS_PORT} https://ipinfo.io/json

EOF
