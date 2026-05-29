// Package tc: 落地 agent 周期拉后端期望限速 (Mbps), 用 tc htb 在出口网卡整形.
//
// 落地机 1:1 独占一个客户, 整形 egress 即可兜住中转双向: 每个被中转的字节 (下载 internet→landing→frontline,
// 上传 frontline→landing→internet) 都要从本机 egress 一次, egress 速率即吞吐瓶颈.
// 期望速率由后端按"占用本落地机的 RUNNING client 的套餐带宽"算出, 0 = 不限.
package tc

import (
	"context"
	"fmt"
	"log"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"

	"nook-agent/internal/client"
	"nook-agent/internal/nic"
)

const desiredPath = "/api/agent/landing/desired-bandwidth"

// rateRe 匹配 tc class 输出里的 "rate 50Mbit" / "rate 50000Kbit" / "rate 1Gbit".
var rateRe = regexp.MustCompile(`rate\s+(\d+(?:\.\d+)?)([KMG]?)bit`)

// Reconciler 周期把本机 tc 限速拉平到后端期望.
type Reconciler struct {
	cli      *client.Client
	iface    string // 配置网卡; "auto"/"" 时探测默认路由
	interval time.Duration
}

func New(cli *client.Client, iface string, interval time.Duration) *Reconciler {
	return &Reconciler{cli: cli, iface: iface, interval: interval}
}

// Run: 启动先收敛一次, 之后每 interval 一次; ctx 取消退出.
func (r *Reconciler) Run(ctx context.Context) {
	if r.interval <= 0 {
		log.Printf("[tc] interval<=0, 不启动")
		return
	}
	log.Printf("[tc] 启动, interval=%v", r.interval)
	t := time.NewTicker(r.interval)
	defer t.Stop()
	r.once(ctx)
	for {
		select {
		case <-ctx.Done():
			log.Printf("[tc] 退出")
			return
		case <-t.C:
			r.once(ctx)
		}
	}
}

func (r *Reconciler) once(ctx context.Context) {
	var rateMbps int
	if err := r.cli.Get(desiredPath, &rateMbps); err != nil {
		log.Printf("[tc] 拉期望限速失败, 跳过本轮: %v", err)
		return
	}
	iface, err := r.resolveIface()
	if err != nil {
		log.Printf("[tc] 解析网卡失败, 跳过本轮: %v", err)
		return
	}
	if err := r.apply(ctx, iface, rateMbps); err != nil {
		log.Printf("[tc] 应用失败 iface=%s rate=%dMbps: %v", iface, rateMbps, err)
	}
}

func (r *Reconciler) resolveIface() (string, error) {
	if r.iface != "" && r.iface != "auto" {
		return r.iface, nil
	}
	return nic.DetectDefaultIface()
}

// apply 比对当前与期望, 仅在不一致时改 (避免每轮重建 qdisc 抖动 active 连接).
func (r *Reconciler) apply(ctx context.Context, iface string, want int) error {
	cur := r.currentRateMbps(ctx, iface)
	if want <= 0 {
		if cur == 0 {
			return nil // 本来就无限速
		}
		log.Printf("[tc] 清除限速 iface=%s (原 %dMbps)", iface, cur)
		return r.clear(ctx, iface)
	}
	if cur == want {
		return nil // 已是目标速率
	}
	log.Printf("[tc] 设限速 iface=%s %dMbps (原 %dMbps)", iface, want, cur)
	return r.setRate(ctx, iface, want)
}

// currentRateMbps 读本机 htb class 速率 (Mbps); 无我们的 htb / 读失败都按 0 (无限速) 处理, apply 会重建.
func (r *Reconciler) currentRateMbps(ctx context.Context, iface string) int {
	out, err := exec.CommandContext(ctx, "tc", "class", "show", "dev", iface).CombinedOutput()
	if err != nil {
		return 0
	}
	m := rateRe.FindStringSubmatch(string(out))
	if m == nil {
		return 0
	}
	val, err := strconv.ParseFloat(m[1], 64)
	if err != nil {
		return 0
	}
	switch m[2] {
	case "K":
		val /= 1000
	case "G":
		val *= 1000
	}
	return int(val + 0.5)
}

// setRate 用 htb root + 单 default class 整形 egress; replace 幂等.
func (r *Reconciler) setRate(ctx context.Context, iface string, mbps int) error {
	rate := strconv.Itoa(mbps) + "mbit"
	cmds := [][]string{
		{"qdisc", "replace", "dev", iface, "root", "handle", "1:", "htb", "default", "1"},
		{"class", "replace", "dev", iface, "parent", "1:", "classid", "1:1", "htb", "rate", rate, "ceil", rate},
	}
	for _, a := range cmds {
		if out, err := exec.CommandContext(ctx, "tc", a...).CombinedOutput(); err != nil {
			return fmt.Errorf("tc %s: %w (out=%s)", strings.Join(a, " "), err, strings.TrimSpace(string(out)))
		}
	}
	return nil
}

// clear 删 root qdisc; 本来就没有视为幂等通过.
func (r *Reconciler) clear(ctx context.Context, iface string) error {
	out, err := exec.CommandContext(ctx, "tc", "qdisc", "del", "dev", iface, "root").CombinedOutput()
	if err != nil {
		low := strings.ToLower(string(out))
		if strings.Contains(low, "no such file") || strings.Contains(low, "invalid argument") {
			return nil
		}
		return fmt.Errorf("tc qdisc del: %w (out=%s)", err, strings.TrimSpace(string(out)))
	}
	return nil
}
