// Package heartbeat 每 1min POST /api/agent/heartbeat; 用于 backend 健康检测.
package heartbeat

import (
	"context"
	"log"
	"time"

	"nook-agent/internal/client"
)

type Reporter struct {
	cli      *client.Client
	interval time.Duration
	version  string
}

func New(cli *client.Client, interval time.Duration, agentVersion string) *Reporter {
	return &Reporter{cli: cli, interval: interval, version: agentVersion}
}

type req struct {
	AgentVersion string `json:"agentVersion"`
}

// Run 阻塞循环 (一般跑在 goroutine 里); ctx 取消时退出.
func (r *Reporter) Run(ctx context.Context) {
	// 启动时立刻发一次 (不等 1min) — 让 backend 尽快看到 agent 上线
	r.tick()
	ticker := time.NewTicker(r.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("[心跳] 退出")
			return
		case <-ticker.C:
			r.tick()
		}
	}
}

func (r *Reporter) tick() {
	if err := r.cli.Post("/api/agent/heartbeat", req{AgentVersion: r.version}, nil); err != nil {
		log.Printf("[心跳] 上报失败: %v", err)
	} else {
		log.Printf("[心跳] 上报成功")
	}
}
