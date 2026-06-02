// Package nic 调本地 vnstat -i <iface> --json 取网卡累计流量 (traffic.total, 跨重启持久), POST 给 backend.
//
// 周期重置由 backend 负责: backend 按重置策略 + 我方重置日, 对累计值做增量累加并到点清零;
// agent 只发"自监控以来的累计总量", 不切周期。计数器被清(整机重置/vnstat库重置)由 backend 增量逻辑兜。
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
	cli        *client.Client
	interval   time.Duration
	iface      string
	bizSampler func() *int64 // landing: 采样 nft socks5 业务流量; nil = 不报 biz (frontline)
}

func New(cli *client.Client, interval time.Duration, iface string, bizSampler func() *int64) *Reporter {
	return &Reporter{cli: cli, interval: interval, iface: iface, bizSampler: bizSampler}
}

type req struct {
	RxBytes      int64  `json:"rxBytes"`
	TxBytes      int64  `json:"txBytes"`
	BizUsedBytes *int64 `json:"bizUsedBytes,omitempty"` // socks5 业务流量(落地机); nil=不报, 后端回退整机 tx
	PeriodStart  string `json:"periodStart"`            // 已弃用: backend 不再依赖, 保留字段兼容
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

// vnstat --json 简化结构 (只取 traffic.total 累计).
type vnstatOutput struct {
	Interfaces []struct {
		Name    string `json:"name"`
		Traffic struct {
			Total struct {
				Rx int64 `json:"rx"`
				Tx int64 `json:"tx"`
			} `json:"total"`
		} `json:"traffic"`
	} `json:"interfaces"`
}

func (r *Reporter) tick() {
	rx, tx, err := r.sampleCumulative()
	if err != nil {
		log.Printf("[nic] vnstat 采样失败: %v", err)
		return
	}
	body := req{RxBytes: rx, TxBytes: tx}
	if r.bizSampler != nil {
		body.BizUsedBytes = r.bizSampler()
	}
	if err := r.cli.Post("/api/agent/nic-traffic", body, nil); err != nil {
		log.Printf("[nic] 上报失败: %v", err)
	} else {
		bizMB := int64(-1) // -1 = 未采到业务流量(frontline 或 nft 未就绪)
		if body.BizUsedBytes != nil {
			bizMB = *body.BizUsedBytes / (1024 * 1024)
		}
		log.Printf("[nic] ok 累计 rx=%dGB tx=%dGB biz=%dMB", rx/(1024*1024*1024), tx/(1024*1024*1024), bizMB)
	}
}

// sampleCumulative 取 vnstat traffic.total (自监控以来累计, 跨重启持久; 仅 vnstat 库被清才归零).
func (r *Reporter) sampleCumulative() (rx, tx int64, err error) {
	iface := r.iface
	if iface == "" || iface == "auto" {
		// 自动探测默认路由网卡 (Linux /proc/net/route); 探测失败回 eth0 兜底
		detected, derr := DetectDefaultIface()
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
		return 0, 0, fmt.Errorf("跑 vnstat (iface=%s) 失败: %w", iface, err)
	}
	var parsed vnstatOutput
	if err := json.Unmarshal(out, &parsed); err != nil {
		return 0, 0, fmt.Errorf("解析 vnstat 输出: %w", err)
	}
	if len(parsed.Interfaces) == 0 {
		return 0, 0, fmt.Errorf("vnstat 无接口数据 (iface=%s, 可能刚装上, 等几分钟再试)", iface)
	}
	t := parsed.Interfaces[0].Traffic.Total
	return t.Rx, t.Tx, nil
}

// DetectDefaultIface 从 /proc/net/route 找 default route (Destination=00000000) 那一行的网卡名.
// 跨发行版稳: 不依赖 ip / ifconfig / iproute2 工具, 直接读 procfs.
func DetectDefaultIface() (string, error) {
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
