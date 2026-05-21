// Package nic 调本地 vnstat -i <iface> --json 取 NIC 流量, 算"当周期累计"后 POST 给 backend.
//
// 周期边界由 backend 决定 (CALENDAR_MONTH/BILLING_CYCLE/FIXED); 起步阶段 agent 只发"本月累计",
// backend 配 quota_reset_policy=CALENDAR_MONTH 时直接拿用.
package nic

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"
	"time"

	"nook-agent/internal/client"
)

type Reporter struct {
	cli       *client.Client
	interval  time.Duration
	iface     string
}

func New(cli *client.Client, interval time.Duration, iface string) *Reporter {
	return &Reporter{cli: cli, interval: interval, iface: iface}
}

type req struct {
	RxBytes     int64  `json:"rxBytes"`
	TxBytes     int64  `json:"txBytes"`
	PeriodStart string `json:"periodStart"`
}

func (r *Reporter) Run(ctx context.Context) {
	if r.interval <= 0 {
		log.Printf("[nic] 间隔 ≤ 0, 不启动 NIC 上报")
		return
	}
	// vnstat 装机后初始几秒可能拿不到数据; 启动时先延 5s 再首次跑
	timer := time.NewTimer(5 * time.Second)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("[nic] 退出")
			return
		case <-timer.C:
			r.tick()
			timer.Reset(r.interval)
		}
	}
}

// vnstat --json 简化结构 (只关心 month traffic).
type vnstatOutput struct {
	Interfaces []struct {
		Name    string `json:"name"`
		Traffic struct {
			Month []struct {
				Date struct {
					Year  int `json:"year"`
					Month int `json:"month"`
				} `json:"date"`
				Rx int64 `json:"rx"`
				Tx int64 `json:"tx"`
			} `json:"month"`
		} `json:"traffic"`
	} `json:"interfaces"`
}

func (r *Reporter) tick() {
	rx, tx, periodStart, err := r.sampleCurrentMonth()
	if err != nil {
		log.Printf("[nic] vnstat 采样失败: %v", err)
		return
	}
	body := req{RxBytes: rx, TxBytes: tx, PeriodStart: periodStart}
	if err := r.cli.Post("/api/agent/nic-traffic", body, nil); err != nil {
		log.Printf("[nic] 上报失败: %v", err)
	} else {
		log.Printf("[nic] ok rx=%dGB tx=%dGB period=%s",
			rx/(1024*1024*1024), tx/(1024*1024*1024), periodStart)
	}
}

func (r *Reporter) sampleCurrentMonth() (rx, tx int64, periodStart string, err error) {
	iface := r.iface
	if iface == "" || iface == "auto" {
		// 自动探测默认路由网卡 (Linux /proc/net/route); 探测失败回 eth0 兜底
		detected, derr := detectDefaultIface()
		if derr != nil {
			log.Printf("[nic] 自动探测网卡失败, 回退 eth0: %v", derr)
			iface = "eth0"
		} else {
			iface = detected
		}
	}
	cmd := exec.Command("vnstat", "-i", iface, "--json")
	out, err := cmd.Output()
	if err != nil {
		return 0, 0, "", fmt.Errorf("跑 vnstat (iface=%s) 失败: %w", iface, err)
	}
	var parsed vnstatOutput
	if err := json.Unmarshal(out, &parsed); err != nil {
		return 0, 0, "", fmt.Errorf("解析 vnstat 输出: %w", err)
	}
	if len(parsed.Interfaces) == 0 || len(parsed.Interfaces[0].Traffic.Month) == 0 {
		return 0, 0, "", fmt.Errorf("vnstat 无月度数据 (iface=%s, 可能刚装上, 等几分钟再试)", iface)
	}
	// 取当月 (UTC 同步; backend 也按 server.billing_cycle_day 决定真实周期, agent 只发原始月度数据)
	now := time.Now().UTC()
	for _, m := range parsed.Interfaces[0].Traffic.Month {
		if m.Date.Year == now.Year() && m.Date.Month == int(now.Month()) {
			return m.Rx, m.Tx,
				fmt.Sprintf("%04d-%02d-01", m.Date.Year, m.Date.Month),
				nil
		}
	}
	// vnstat 偶尔月初拿不到当月行, fallback 用最后一条
	last := parsed.Interfaces[0].Traffic.Month[len(parsed.Interfaces[0].Traffic.Month)-1]
	return last.Rx, last.Tx,
		fmt.Sprintf("%04d-%02d-01", last.Date.Year, last.Date.Month),
		nil
}

// detectDefaultIface 从 /proc/net/route 找 default route (Destination=00000000) 那一行的网卡名.
// 跨发行版稳: 不依赖 ip / ifconfig / iproute2 工具, 直接读 procfs.
func detectDefaultIface() (string, error) {
	f, err := os.Open("/proc/net/route")
	if err != nil {
		return "", err
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	scanner.Scan() // header: Iface Destination Gateway ...
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) >= 2 && fields[1] == "00000000" {
			return fields[0], nil
		}
	}
	return "", fmt.Errorf("/proc/net/route 没找到默认路由")
}
