// nook-landing-agent: 落地机 agent. 跑 tc 限速 reconcile (socks5 仍由 backend SSH 装).
//
// 编译: go build -ldflags '-X main.Version=landing-0.8.0 -s -w' -o nook-landing-0.8.0-linux-amd64 ./cmd/landing
package main

import (
	"context"
	"log"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	internalExec "nook-agent/internal/executor"
	"nook-agent/internal/tc"
)

var Version = "landing-dev"

func main() {
	agentcore.Run(Version, registerLanding)
}

// 落地机角色注册器: 挂 tc 限速 reconcile (按后端期望带宽整形出口网卡); socks5 接管仍走 backend SSH.
// 跟 frontline 共用心跳/NIC/poller/升级/改配置.
func registerLanding(_ *internalExec.Dispatcher, cfg *config.Config, cli *client.Client) []agentcore.Goroutine {
	interval := cfg.LandingBandwidthReconcileInterval()
	if interval <= 0 {
		log.Printf("[landing] 启动 (未配 landing.bandwidth_reconcile_interval_seconds, 不挂 tc 限速)")
		return nil
	}
	rec := tc.New(cli, cfg.NIC.Interface, interval)
	log.Printf("[landing] 启动, tc 限速 reconcile 周期=%v (iface=%s)", interval, cfg.NIC.Interface)
	return []agentcore.Goroutine{
		func(ctx context.Context) { rec.Run(ctx) },
	}
}
