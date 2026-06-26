// nook-landing-agent: 落地机 agent.
//
// 一个 reconcile 循环拉后端"落地机期望配置"(出口限速 + socks5 端口) → 应用 tc 限速 + 维护 nft 业务流量计数器;
// 计数器读数作为 nic 上报的业务流量采样源。心跳 / NIC 在 agentcore 共用。
//
// 编译: go build -ldflags '-X main.Version=landing-0.x.y -s -w' -o nook-landing-...-linux-amd64 ./cmd/landing
package main

import (
	"context"
	"io"
	"log"
	"strconv"
	"time"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/landing/meter"
	"nook-agent/internal/landing/socks5deploy"
	"nook-agent/internal/landing/tc"
	"nook-agent/internal/shared/client"
	"nook-agent/internal/shared/config"
)

// Version 编译时 ldflags 注入; 命名约定 "landing-X.Y.Z".
var Version = "landing-dev"

func main() {
	agentcore.Run(Version, registerLanding)
}

// landingDesired 跟后端 LandingDesiredRespVO 对齐.
type landingDesired struct {
	BandwidthMbps int `json:"bandwidthMbps"`
	Socks5Port    int `json:"socks5Port"`
}

// registerLanding: 落地机角色挂一个 reconcile 循环 —— 一次拉取"期望配置"(限速 + socks5 端口),
// 分发给 tc 整形和 nft 计数器维护; 计数器 Sample 作为 nic 业务流量采样源.
func registerLanding(cfg *config.Config, cli *client.Client) agentcore.RoleComponents {
	interval := cfg.LandingBandwidthReconcileInterval()
	limiter := tc.New(cfg.NIC.Interface)
	m := meter.New()
	loop := func(ctx context.Context) {
		if interval <= 0 {
			log.Printf("[落地机] 未配 landing.bandwidth_reconcile_interval_seconds, 不挂限速/计量")
			return
		}
		log.Printf("[落地机] 启动, 拉取周期=%d秒 (网卡=%s)", int(interval.Seconds()), cfg.NIC.Interface)
		t := time.NewTicker(interval)
		defer t.Stop()
		reconcileOnce(ctx, cli, limiter, m)
		for {
			select {
			case <-ctx.Done():
				log.Printf("[落地机] 退出")
				return
			case <-t.C:
				reconcileOnce(ctx, cli, limiter, m)
			}
		}
	}
	return agentcore.RoleComponents{
		Goroutines:    []agentcore.Goroutine{loop},
		NicBizSampler: m.SampleUpDown,
		// 控制接口 /socks5/deploy: 后台下发 dante 期望态, agent 本地装机 (取代后台 SSH 推 bash)
		Socks5Deploy: func(ctx context.Context, body []byte, out io.Writer) error {
			return socks5deploy.Deploy(ctx, body, out)
		},
	}
}

// reconcileOnce: 拉一次落地机期望配置, 分发给出口限速 + nft 计数器维护.
func reconcileOnce(ctx context.Context, cli *client.Client, limiter *tc.Limiter, m *meter.Meter) {
	var d landingDesired
	if err := cli.Get("/api/agent/landing/desired", &d); err != nil {
		log.Printf("[落地机] 拉期望配置失败, 跳过本轮: %v", err)
		return
	}
	band := "不限"
	if d.BandwidthMbps > 0 {
		band = strconv.Itoa(d.BandwidthMbps) + "Mbps"
	}
	port := "未配置"
	if d.Socks5Port > 0 {
		port = strconv.Itoa(d.Socks5Port)
	}
	log.Printf("[落地机] 拉到期望配置 出口限速=%s socks5端口=%s", band, port)
	limiter.Apply(ctx, d.BandwidthMbps)
	m.Ensure(ctx, d.Socks5Port)
}
