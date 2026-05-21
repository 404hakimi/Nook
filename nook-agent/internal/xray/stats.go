// Package xray (stats.go): 每 5min 调 statsquery 上报用户层流量给 backend.
package xray

import (
	"context"
	"log"
	"time"

	"nook-agent/internal/client"
)

// StatsReporter 周期采集 xray user traffic + POST /api/agent/xray-traffic.
type StatsReporter struct {
	cli      *client.Client
	xray     *Client
	interval time.Duration
	// reset=true 让 xray 清零 user counter, 下轮拿到的是增量;
	// 配 false 拿累计, backend 算 delta (跟 NIC 一样). 起步用 false (累计模式更稳).
	reset bool
}

func NewStatsReporter(cli *client.Client, xray *Client, interval time.Duration) *StatsReporter {
	return &StatsReporter{cli: cli, xray: xray, interval: interval, reset: false}
}

type xrayTrafficRow struct {
	Email     string `json:"email"`
	UpBytes   int64  `json:"upBytes"`
	DownBytes int64  `json:"downBytes"`
}

type xrayTrafficReq struct {
	Stats []xrayTrafficRow `json:"stats"`
}

func (r *StatsReporter) Run(ctx context.Context) {
	if r.interval <= 0 {
		log.Printf("[xray-stats] 间隔 ≤ 0, 不启动 xray 流量上报")
		return
	}
	// 启动延 10s 让 xray 稳定 (装机后 systemd unit 启动早于本 agent 已经在跑就无影响)
	timer := time.NewTimer(10 * time.Second)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("[xray-stats] 退出")
			return
		case <-timer.C:
			r.tick(ctx)
			timer.Reset(r.interval)
		}
	}
}

func (r *StatsReporter) tick(ctx context.Context) {
	stats, err := r.xray.StatsQuery(ctx, r.reset)
	if err != nil {
		log.Printf("[xray-stats] statsquery 失败: %v", err)
		return
	}
	if len(stats) == 0 {
		log.Printf("[xray-stats] 无 user 流量数据 (xray 可能未配 user / 刚启动)")
		return
	}
	rows := make([]xrayTrafficRow, 0, len(stats))
	for _, s := range stats {
		rows = append(rows, xrayTrafficRow{Email: s.Email, UpBytes: s.UpBytes, DownBytes: s.DownBytes})
	}
	if err := r.cli.Post("/api/agent/xray-traffic", xrayTrafficReq{Stats: rows}, nil); err != nil {
		log.Printf("[xray-stats] 上报失败: %v", err)
		return
	}
	log.Printf("[xray-stats] ok userCount=%d", len(rows))
}
