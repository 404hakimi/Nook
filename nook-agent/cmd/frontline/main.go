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
		log.Fatalf("[线路机] 配置 xray 段不完整: 程序路径=%q 接口端口=%d; 装机时应已填好", bin, apiPort)
	}
	if _, err := os.Stat(bin); err != nil {
		log.Printf("[线路机] xray 程序 %s 不存在 (%v), 不启动对账", bin, err)
		return agentcore.RoleComponents{}
	}
	log.Printf("[线路机] xray 已就绪 (程序=%s 接口端口=%d), 启动对账", bin, apiPort)
	xrayCli := xray.New(bin, apiPort)

	// reconcile 周期: yaml 未配则回退 stats_interval (历史字段, 都 ~5min)
	reconcileInterval := cfg.XrayReconcileInterval()
	if reconcileInterval <= 0 {
		reconcileInterval = cfg.XrayStatsInterval()
	}
	rec := reconcile.New(cli, xrayCli, reconcileInterval)
	log.Printf("[线路机] 对账周期=%d秒", int(reconcileInterval.Seconds()))

	return agentcore.RoleComponents{
		Goroutines: []agentcore.Goroutine{
			func(ctx context.Context) { rec.Run(ctx) },
		},
	}
}
