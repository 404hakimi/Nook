#!/usr/bin/env bash
# 单独 ops 脚本 (swap/bbr/...) 的公共头: log/warn/err + root 校验.
# install 链路有自己的 00-prepare-env, 不复用这份.

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
