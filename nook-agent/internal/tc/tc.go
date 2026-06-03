// Package tc: 落地机出口限速 (tc htb). 期望限速值由 landing 循环从后端拉来后调 Apply, 本包不自拉不自循环.
//
// 落地机 1:1 独占一个客户, 整形 egress 即可兜住中转双向: 上下行都从本机 egress 过一次, egress 速率即吞吐瓶颈.
package tc

import (
	"context"
	"fmt"
	"log"
	"os/exec"
	"regexp"
	"strconv"
	"strings"

	"nook-agent/internal/nic"
)

// rateRe 匹配 tc class 输出里的 "rate 50Mbit" / "rate 50000Kbit" / "rate 1Gbit".
var rateRe = regexp.MustCompile(`rate\s+(\d+(?:\.\d+)?)([KMG]?)bit`)

// Limiter 把出口网卡限速拉平到期望值; 无状态, 由 landing 循环周期调 Apply.
type Limiter struct {
	iface string // 配置网卡; "auto"/"" 时探测默认路由
}

func New(iface string) *Limiter {
	return &Limiter{iface: iface}
}

// Apply: 将出口限速收敛到 mbps (0=不限); 仅当前与期望不一致时改 (避免每轮重建 qdisc 抖动 active 连接).
func (l *Limiter) Apply(ctx context.Context, mbps int) {
	iface, err := l.resolveIface()
	if err != nil {
		log.Printf("[限速] 解析网卡失败, 跳过本轮: %v", err)
		return
	}
	cur := l.currentRateMbps(ctx, iface)
	if mbps <= 0 {
		if cur == 0 {
			return // 本来就无限速
		}
		log.Printf("[限速] 清除限速 网卡=%s (原 %dMbps)", iface, cur)
		if err := l.clear(ctx, iface); err != nil {
			log.Printf("[限速] 清除限速失败 网卡=%s: %v", iface, err)
		}
		return
	}
	if cur == mbps {
		return // 已是目标速率
	}
	log.Printf("[限速] 设限速 网卡=%s %dMbps (原 %dMbps)", iface, mbps, cur)
	if err := l.setRate(ctx, iface, mbps); err != nil {
		log.Printf("[限速] 设限速失败 网卡=%s %dMbps: %v", iface, mbps, err)
	}
}

func (l *Limiter) resolveIface() (string, error) {
	if l.iface != "" && l.iface != "auto" {
		return l.iface, nil
	}
	return nic.DetectDefaultIface()
}

// currentRateMbps 读本机 htb class 速率 (Mbps); 无我们的 htb / 读失败都按 0 (无限速) 处理.
func (l *Limiter) currentRateMbps(ctx context.Context, iface string) int {
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

// setRate 用 htb root + 单 default class 整形 egress; root 建一次, 速率改动落 class.
func (l *Limiter) setRate(ctx context.Context, iface string, mbps int) error {
	if err := l.ensureRootQdisc(ctx, iface); err != nil {
		return err
	}
	rate := strconv.Itoa(mbps) + "mbit"
	a := []string{"class", "replace", "dev", iface, "parent", "1:", "classid", "1:1", "htb", "rate", rate, "ceil", rate}
	if out, err := exec.CommandContext(ctx, "tc", a...).CombinedOutput(); err != nil {
		return fmt.Errorf("tc %s: %w (out=%s)", strings.Join(a, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

// ensureRootQdisc 确保 htb root 存在: 已是我们的 htb 就不动; 否则把默认 root replace 成 htb.
func (l *Limiter) ensureRootQdisc(ctx context.Context, iface string) error {
	out, err := exec.CommandContext(ctx, "tc", "qdisc", "show", "dev", iface).CombinedOutput()
	if err == nil && strings.Contains(string(out), "qdisc htb 1:") {
		return nil
	}
	a := []string{"qdisc", "replace", "dev", iface, "root", "handle", "1:", "htb", "default", "1"}
	if out, err := exec.CommandContext(ctx, "tc", a...).CombinedOutput(); err != nil {
		return fmt.Errorf("tc %s: %w (out=%s)", strings.Join(a, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

// clear 删 root qdisc; 本来就没有视为幂等通过.
func (l *Limiter) clear(ctx context.Context, iface string) error {
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
