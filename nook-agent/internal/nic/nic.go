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
	"strings"
	"time"

	"nook-agent/internal/client"
	"nook-agent/internal/execx"
)

type Reporter struct {
	cli        *client.Client
	interval   time.Duration
	iface      string
	bizSampler func() (up, down *int64) // landing: 采样 nft socks5 业务上下行; nil = 不报 biz (frontline)
}

func New(cli *client.Client, interval time.Duration, iface string, bizSampler func() (up, down *int64)) *Reporter {
	return &Reporter{cli: cli, interval: interval, iface: iface, bizSampler: bizSampler}
}

type req struct {
	RxBytes      int64  `json:"rxBytes"`
	TxBytes      int64  `json:"txBytes"`
	BizUpBytes   *int64 `json:"bizUpBytes,omitempty"`   // socks5 用户上行(落地机); nil=不报
	BizDownBytes *int64 `json:"bizDownBytes,omitempty"` // socks5 用户下行(落地机); nil=不报
}

func (r *Reporter) Run(ctx context.Context) {
	if r.interval <= 0 {
		log.Printf("[流量] 上报间隔 ≤ 0, 不启动流量上报")
		return
	}
	// vnstat 装机后初始几秒可能拿不到数据; 启动时先延 5s 再首次跑
	timer := time.NewTimer(5 * time.Second)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("[流量] 退出")
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
		log.Printf("[流量] 网卡采样失败: %v", err)
		return
	}
	body := req{RxBytes: rx, TxBytes: tx}
	if r.bizSampler != nil {
		body.BizUpBytes, body.BizDownBytes = r.bizSampler()
	}
	if err := r.cli.Post("/api/agent/nic-traffic", body, nil); err != nil {
		log.Printf("[流量] 上报失败: %v", err)
		return
	}
	biz := "未采集" // 线路机不计业务流量, 落地机计数器未就绪时也为此值
	if body.BizUpBytes != nil || body.BizDownBytes != nil {
		biz = fmt.Sprintf("上行=%s 下行=%s", megaBytes(deref(body.BizUpBytes)), megaBytes(deref(body.BizDownBytes)))
	}
	log.Printf("[流量] 上报成功 累计 入站=%s 出站=%s 业务[%s]", humanBytes(rx), humanBytes(tx), biz)
}

// deref 取 *int64 值, nil 视为 0 (仅日志展示用).
func deref(p *int64) int64 {
	if p == nil {
		return 0
	}
	return *p
}

// megaBytes 固定以 MB 为单位格式化 (业务流量统一看 MB, 便于跨行比对);
// 不足 0.01MB 的极小流量多给 2 位小数, 避免又显示成 0.00MB.
func megaBytes(n int64) string {
	mb := float64(n) / (1024 * 1024)
	if mb > 0 && mb < 0.01 {
		return fmt.Sprintf("%.4fMB", mb)
	}
	return fmt.Sprintf("%.2fMB", mb)
}

// humanBytes 把字节数格式化成带 2 位小数的友好单位 (B/KB/MB/GB/TB), 保留精度, 不把小流量截成 0.
func humanBytes(n int64) string {
	const unit = 1024
	if n < unit {
		return fmt.Sprintf("%dB", n)
	}
	units := []string{"KB", "MB", "GB", "TB", "PB"}
	v := float64(n) / unit
	i := 0
	for v >= unit && i < len(units)-1 {
		v /= unit
		i++
	}
	return fmt.Sprintf("%.2f%s", v, units[i])
}

// sampleCumulative 取 vnstat traffic.total (自监控以来累计, 跨重启持久; 仅 vnstat 库被清才归零).
func (r *Reporter) sampleCumulative() (rx, tx int64, err error) {
	iface := r.iface
	if iface == "" || iface == "auto" {
		// 自动探测默认路由网卡 (Linux /proc/net/route); 探测失败回 eth0 兜底
		detected, derr := DetectDefaultIface()
		if derr != nil {
			log.Printf("[流量] 自动探测网卡失败, 回退 eth0: %v", derr)
			iface = "eth0"
		} else {
			iface = detected
		}
	}
	// 单次超时: vnstat 卡死(库锁/IO stall)不能把整条上报 loop 永久阻塞. tick 无 ctx 形参, 用 Background.
	cmd, cancel := execx.Command(context.Background(), execx.DefaultTimeout, "vnstat", "-i", iface, "--json")
	defer cancel()
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
