// nook-frontline-agent: 线路机 agent. xray reconcile (启动校验 yaml 里 xray 块齐).
//
// 编译: go build -ldflags '-X main.Version=frontline-0.9.0 -s -w' -o nook-frontline-0.9.0-linux-amd64 ./cmd/frontline
package main

import (
	"context"
	"io"
	"log"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	"nook-agent/internal/reconcile"
	"nook-agent/internal/xray"
	"nook-agent/internal/xraydeploy"
)

// Version 编译时 ldflags 注入; 命名约定 "frontline-X.Y.Z".
var Version = "frontline-dev"

func main() {
	agentcore.Run(Version, registerFrontline)
}

// registerFrontline: frontline 角色挂 xray reconcile; yaml 里 xray 段必填.
// per-user 流量统计已移除 (计量改落地机 socks5 上下行, 见 trade trade_subscription_traffic), 不再采集 xray statsquery.
func registerFrontline(cfg *config.Config, cli *client.Client) agentcore.RoleComponents {
	bin := cfg.Xray.Bin
	apiPort := cfg.Xray.APIPort
	// 完整重排: config 装机时已写固定 xray 段; xray 可能还没部署, 不在启动时拦,
	// 由 reconcile 每轮探 binary 就绪 — 没就绪记日志跳过, 部署好后自动对账, 无需重启 agent
	if bin == "" || apiPort == 0 {
		log.Printf("[线路机] xray 配置缺失 (config 应有固定 xray 段), 不启动对账")
		return agentcore.RoleComponents{}
	}
	log.Printf("[线路机] 启动对账 (xray bin=%s 接口端口=%d, 运行时探就绪)", bin, apiPort)
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
		// 后台 POST /xray/deploy 时本地装机 (装到 config 的 xray.bin 路径); reconcile 装好后自动接上灌用户.
		XrayDeploy: func(ctx context.Context, body []byte, out io.Writer) error {
			return xraydeploy.Deploy(ctx, bin, apiPort, body, out)
		},
	}
}
