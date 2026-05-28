// nook-frontline-agent: 线路机 agent. xray executor + stats collector (启动校验 yaml 里 xray 块齐).
//
// 编译: go build -ldflags '-X main.Version=frontline-0.7.0 -s -w' -o nook-frontline-0.7.0-linux-amd64 ./cmd/frontline
package main

import (
	"context"
	"log"
	"os"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	internalExec "nook-agent/internal/executor"
	"nook-agent/internal/reconcile"
	"nook-agent/internal/xray"
)

// Version 编译时 ldflags 注入; 命名约定 "frontline-X.Y.Z".
var Version = "frontline-dev"

func main() {
	agentcore.Run(Version, registerFrontline)
}

// registerFrontline: frontline 角色挂 xray executor + stats collector; yaml 里 xray 段必填.
func registerFrontline(disp *internalExec.Dispatcher, cfg *config.Config, cli *client.Client) []agentcore.Goroutine {
	bin := cfg.Xray.Bin
	apiPort := cfg.Xray.APIPort
	statsInterval := cfg.XrayStatsInterval()
	if bin == "" || apiPort == 0 || statsInterval == 0 {
		log.Fatalf("[frontline] yaml xray 段不完整: bin=%q api_port=%d stats_interval=%v; backend 装机时应已填", bin, apiPort, statsInterval)
	}
	if _, err := os.Stat(bin); err != nil {
		log.Printf("[frontline] xray bin %s 文件不存在 (%v), 不挂 collector", bin, err)
		return nil
	}
	log.Printf("[frontline] xray 已装 (bin=%s apiPort=%d), 挂载 executor + stats collector", bin, apiPort)
	internalExec.NewXrayExecutor(bin, apiPort).Register(disp)
	xrayCli := xray.New(bin, apiPort)
	statsRep := xray.NewStatsReporter(cli, xrayCli, statsInterval)

	// reconcile 周期: yaml 未配则回退 stats_interval (都 ~5min)
	reconcileInterval := cfg.XrayReconcileInterval()
	if reconcileInterval <= 0 {
		reconcileInterval = statsInterval
	}
	rec := reconcile.New(cli, xrayCli, reconcileInterval)
	log.Printf("[frontline] reconcile 周期=%v", reconcileInterval)

	return []agentcore.Goroutine{
		func(ctx context.Context) { statsRep.Run(ctx) },
		func(ctx context.Context) { rec.Run(ctx) },
	}
}
