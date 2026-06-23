// Package bootstrap: agent 启动时的机器级幂等准备.
//
// 当前只做"时间相关": 控制通道 (后台 ↔ agent) 用 epoch 毫秒时间戳 + ±窗口防重放, 双端时钟必须准.
// 时间戳是 UTC epoch 毫秒, 与时区无关 —— 真正治本的是 NTP 时间同步, 时区只为日志/运维可读.
// agent 每次启动跑一遍 (幂等), 不依赖装机脚本是否设过, 也能在重装/迁移后自愈.
//
// 失败一律仅告警不阻断 (非 root / 无 timedatectl 的环境跳过, 不拖垮 agent 启动).
package bootstrap

import (
	"context"
	"log"
	"os/exec"
	"strings"
	"time"
)

// cmdTimeout 单条 bootstrap 命令上限; timedatectl 通常瞬时, 给足余量又不至卡死启动.
const cmdTimeout = 10 * time.Second

// Run 跑机器级幂等准备 (时区 + NTP 时间同步); 每条 best-effort.
func Run() {
	// NTP 时间同步: 控制通道时间戳新鲜度校验的前提 (双端时钟漂移 > 窗口会令合法请求被拒).
	runBest("启用 NTP 时间同步", "timedatectl", "set-ntp", "true")
	// 时区: 不影响 epoch 毫秒, 仅为日志/运维可读一致.
	runBest("设置时区 Asia/Shanghai", "timedatectl", "set-timezone", "Asia/Shanghai")
}

func runBest(desc, name string, args ...string) {
	ctx, cancel := context.WithTimeout(context.Background(), cmdTimeout)
	defer cancel()
	if out, err := exec.CommandContext(ctx, name, args...).CombinedOutput(); err != nil {
		log.Printf("[bootstrap] %s 失败(忽略): %v %s", desc, err, strings.TrimSpace(string(out)))
		return
	}
	log.Printf("[bootstrap] %s ✔", desc)
}
