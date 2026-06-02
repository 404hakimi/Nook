// nook-frontline-agent: 线路机 agent. xray reconcile (启动校验 yaml 里 xray 块齐).
//
// 编译: go build -ldflags '-X main.Version=frontline-0.9.0 -s -w' -o nook-frontline-0.9.0-linux-amd64 ./cmd/frontline
package main

import (
	"context"
	"log"
	"os"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	"nook-agent/internal/reconcile"
	"nook-agent/internal/xray"
)

// Version 编译时 ldflags 注入; 命名约定 "frontline-X.Y.Z".
var Version = "frontline-dev"

func main() {
	agentcore.Run(Version, registerFrontline)
}

// registerFrontline: frontline 角色挂 xray reconcile; yaml 里 xray 段必填.
// per-user 流量统计已移除 (计量改落地机 NIC, 见 trade member_plan_traffic), 不再采集 xray statsquery.
func registerFrontline(cfg *config.Config, cli *client.Client) agentcore.RoleComponents {
	bin := cfg.Xray.Bin
	apiPort := cfg.Xray.APIPort
	if bin == "" || apiPort == 0 {
		log.Fatalf("[frontline] yaml xray 段不完整: bin=%q api_port=%d; backend 装机时应已填", bin, apiPort)
	}
	if _, err := os.Stat(bin); err != nil {
		log.Printf("[frontline] xray bin %s 文件不存在 (%v), 不挂 reconcile", bin, err)
		return agentcore.RoleComponents{}
	}
	log.Printf("[frontline] xray 已装 (bin=%s apiPort=%d), 挂载 reconcile", bin, apiPort)
	xrayCli := xray.New(bin, apiPort)

	// reconcile 周期: yaml 未配则回退 stats_interval (历史字段, 都 ~5min)
	reconcileInterval := cfg.XrayReconcileInterval()
	if reconcileInterval <= 0 {
		reconcileInterval = cfg.XrayStatsInterval()
	}
	rec := reconcile.New(cli, xrayCli, reconcileInterval)
	log.Printf("[frontline] reconcile 周期=%v", reconcileInterval)

	return agentcore.RoleComponents{
		Goroutines: []agentcore.Goroutine{
			func(ctx context.Context) { rec.Run(ctx) },
		},
	}
}
