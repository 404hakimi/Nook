#!/usr/bin/env bash
# ===== nook ops 脚本公共头 =====
# 单独运维操作 (swap/bbr/...) 用; install 链路另有 modules/00-prepare-env 自带这段.
# 不做 OS 校验 (假设服务器已通过 install 部署过, 满足 Ubuntu 22+ amd64/arm64 前提).

set -euo pipefail

if [ -t 1 ]; then
    GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
else
    GREEN=''; YELLOW=''; RED=''; NC=''
fi
log()  { echo -e "${GREEN}[+]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[x]${NC} $*" >&2; }

[[ $EUID -eq 0 ]] || { err "需 root 权限"; exit 1; }
