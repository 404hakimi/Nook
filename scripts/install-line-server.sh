#!/usr/bin/env bash
#
# Nook 线路服务器(中转节点)一键安装脚本 — Ubuntu 22+
#
# 这台机器干啥：
#   - 跑 Xray 内核
#   - 暴露一个 vmess 入站给客户连
#   - Xray gRPC API 监听 127.0.0.1:62789, 由 nook 通过 SSH 隧道远程调度
#   - clients / outbounds / routing rules 全由 nook 通过 gRPC 在内存里增量管理
#
# 不装啥：
#   - 不装 3x-ui (你想要监控/日志 UI 自己另装,但请知悉:
#                3x-ui 启动会用自己 DB 模板覆盖 /usr/local/etc/xray/config.json,
#                破坏本脚本写好的 grpc-api 配置. 想共存的话需要进 3x-ui 后台
#                Settings → Xray Configs 也开启 API + 注入相同 inbound)
#
# 用法:
#   sudo bash install-line-server.sh
#
# 环境变量(可选):
#   VMESS_PORT       默认 443; 客户连的 vmess 入站端口
#   XRAY_API_PORT    默认 62789; Xray gRPC API 端口(loopback only)
#   LOG_DIR          默认 /var/log/xray
#

set -euo pipefail

# ===== 全局变量 =====
VMESS_PORT="${VMESS_PORT:-443}"
XRAY_API_PORT="${XRAY_API_PORT:-62789}"
LOG_DIR="${LOG_DIR:-/var/log/xray}"
XRAY_CONFIG=/usr/local/etc/xray/config.json

# ===== 颜色 =====
# 仅在 TTY 输出; 非 TTY (如 nook 远程调用) 走纯文本, 避免 ANSI 码污染流式日志
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

[[ $EUID -eq 0 ]] || { err "请以 root 运行 (sudo bash install-line-server.sh)"; exit 1; }

[[ -f /etc/os-release ]] || { err "无法识别系统, 需要 Ubuntu 22+"; exit 1; }
. /etc/os-release
[[ "$ID" == "ubuntu" && "${VERSION_ID%.*}" -ge 22 ]] \
    || { err "仅支持 Ubuntu 22.04+, 当前: $ID $VERSION_ID"; exit 1; }

ARCH=$(dpkg --print-architecture)
[[ "$ARCH" == "amd64" || "$ARCH" == "arm64" ]] \
    || { err "仅支持 amd64 / arm64, 当前: $ARCH"; exit 1; }

log "系统检查通过: Ubuntu $VERSION_ID $ARCH"

# ===== Step 1. 系统基础 =====

log "更新系统 + 装基础工具..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl wget unzip ca-certificates ufw

# ===== Step 2. 装 Xray =====
# 用官方 install-release.sh; 它装 binary + systemd + 必要目录

if command -v xray >/dev/null 2>&1 && [[ -f /etc/systemd/system/xray.service ]]; then
    XRAY_VER=$(xray version 2>/dev/null | head -1 || echo "?")
    warn "检测到 Xray 已安装: $XRAY_VER (跳过下载)"
else
    log "下载安装 Xray (官方 install-release.sh)..."
    bash -c "$(curl -fsSL https://raw.githubusercontent.com/XTLS/Xray-install/main/install-release.sh)" @ install
    XRAY_VER=$(xray version | head -1)
    log "Xray 安装完成: $XRAY_VER"
fi

systemctl stop xray >/dev/null 2>&1 || true

# ===== Step 3. 写 nook 标配 xray.json =====
# 关键段:
#   api {Handler/Routing/Stats/Logger}  → 让 nook 通过 gRPC 操作 Xray
#   stats {}                             → 启用流量统计
#   policy.system + policy.levels.0     → 启用 inbound/outbound/user 级流量计数
#   inbounds[0]   = api dokodemo-door 监听 127.0.0.1:$XRAY_API_PORT
#   inbounds[1]   = vmess 业务入站 监听 0.0.0.0:$VMESS_PORT, 客户端列表 = []
#   outbounds     = api(freedom) + direct(freedom 兜底, 没匹配 routing rule 的流量从本机出)
#                   nook 会在运行时按 client 加 socks5 outbound
#   routing.rules = 仅 api 自身规则; nook 会在运行时按 client.email 加路由

mkdir -p "$LOG_DIR" "$(dirname "$XRAY_CONFIG")"

# 备份旧配置
if [[ -f "$XRAY_CONFIG" ]]; then
    BACKUP="${XRAY_CONFIG}.bak.$(date +%s)"
    cp "$XRAY_CONFIG" "$BACKUP"
    log "原 Xray 配置已备份到 $BACKUP"
fi

log "写入 nook 标配 xray.json..."

cat > "$XRAY_CONFIG" <<EOF
{
  "log": {
    "access": "$LOG_DIR/access.log",
    "error": "$LOG_DIR/error.log",
    "loglevel": "warning"
  },
  "api": {
    "tag": "api",
    "services": ["HandlerService", "LoggerService", "StatsService", "RoutingService"]
  },
  "stats": {},
  "policy": {
    "system": {
      "statsInboundUplink": true,
      "statsInboundDownlink": true,
      "statsOutboundUplink": true,
      "statsOutboundDownlink": true
    },
    "levels": {
      "0": {
        "statsUserUplink": true,
        "statsUserDownlink": true
      }
    }
  },
  "inbounds": [
    {
      "tag": "api",
      "listen": "127.0.0.1",
      "port": $XRAY_API_PORT,
      "protocol": "dokodemo-door",
      "settings": { "address": "127.0.0.1" }
    },
    {
      "tag": "vmess-in",
      "listen": "0.0.0.0",
      "port": $VMESS_PORT,
      "protocol": "vmess",
      "settings": { "clients": [] },
      "streamSettings": { "network": "tcp" },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls"] }
    }
  ],
  "outbounds": [
    { "tag": "api",    "protocol": "freedom" },
    { "tag": "direct", "protocol": "freedom" }
  ],
  "routing": {
    "domainStrategy": "AsIs",
    "rules": [
      { "type": "field", "inboundTag": ["api"], "outboundTag": "api" }
    ]
  }
}
EOF

# 给 xray 用户权限读日志目录(官方安装会建 'nobody:nogroup' 服务用户)
chmod 644 "$XRAY_CONFIG"
chown -R nobody:nogroup "$LOG_DIR" 2>/dev/null || true

# 用 xray 自己 test 配置 (语法 + 字段校验)
log "验证 Xray 配置..."
# Xray CLI 兼容: 新版(2024+) 'xray run -test -c'; 老版(1.x) 'xray test -c'
test_xray_config() {
    xray run -test -c "$1" >/dev/null 2>&1 \
        || xray test -c "$1" >/dev/null 2>&1
}
if ! test_xray_config "$XRAY_CONFIG"; then
    err "Xray 配置语法错误! 看输出:"
    xray run -test -c "$XRAY_CONFIG" 2>&1 || xray test -c "$XRAY_CONFIG" 2>&1 || true
    exit 1
fi
log "✔ 配置验证通过"

# ===== Step 4. 防火墙 =====

log "配置防火墙..."
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp comment "SSH" >/dev/null
ufw allow "$VMESS_PORT/tcp" comment "vmess inbound" >/dev/null
# Xray gRPC API 走 loopback + SSH 隧道, 不开公网
ufw --force enable >/dev/null

# ===== Step 5. 启动 Xray =====

log "启动 Xray service..."
systemctl daemon-reload
systemctl enable xray >/dev/null 2>&1
systemctl restart xray
sleep 2

if ! systemctl is-active --quiet xray; then
    err "Xray 启动失败!"
    journalctl -u xray -n 30 --no-pager
    exit 1
fi

# 验证 gRPC 端口监听
sleep 1
if ss -ltn 2>/dev/null | grep -q "127.0.0.1:$XRAY_API_PORT "; then
    log "✔ Xray gRPC API 在监听 127.0.0.1:$XRAY_API_PORT"
else
    err "Xray gRPC 端口未监听, 安装失败"
    journalctl -u xray -n 30 --no-pager
    exit 1
fi

# 验证 vmess 端口监听
if ss -ltn 2>/dev/null | grep -q ":$VMESS_PORT "; then
    log "✔ vmess inbound 在监听 0.0.0.0:$VMESS_PORT"
else
    warn "vmess 端口未监听 (但 Xray 起来了); 可能配置问题"
fi

# ===== 最后输出 =====

PUBLIC_IP=$(curl -s4 --max-time 5 ifconfig.me 2>/dev/null || echo "<your-ip>")

cat <<EOF

${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}
${GREEN}                   线路服务器安装完成!${NC}
${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}

  Xray 版本:         $XRAY_VER
  Xray 配置:         $XRAY_CONFIG
  Xray 日志:         $LOG_DIR/{access,error}.log

  vmess inbound:     0.0.0.0:${VMESS_PORT} (公网, 客户端连这个)
  gRPC API:          127.0.0.1:${XRAY_API_PORT} (仅本地, nook 走 SSH 隧道)

${YELLOW}━━━━━━━━━━━━━━ 下一步在 nook 后台 ━━━━━━━━━━━━━━${NC}

  服务器管理 → 新增:
    host:               ${PUBLIC_IP}
    SSH 用户/密码或私钥
    backendType:        xray-grpc
    xrayGrpcHost:       127.0.0.1
    xrayGrpcPort:       ${XRAY_API_PORT}
    panel(留空):        本脚本不装 3x-ui
                        要监控 UI 自己另装, 但请知悉它会
                        覆盖 /usr/local/etc/xray/config.json
                        破坏 grpc-api, 需手动合并配置.

${YELLOW}━━━━━━━━━━━━━━ 系统监控可选 ━━━━━━━━━━━━━━${NC}

  推荐: Netdata (装完只读监控, 不动 Xray):
    bash <(curl -Ss https://my-netdata.io/kickstart.sh) --dont-wait

EOF
