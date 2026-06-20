// Package tc: 落地机出口限速 (tc htb). 期望限速值由 landing 循环从后端拉来后调 Apply, 本包不自拉不自循环.
//
// 落地机 1:1 独占一个客户, 整形 egress 即可兜住中转双向: 上下行都从本机 egress 过一次, egress 速率即吞吐瓶颈.
package tc

import (
	"context"
	"fmt"
	"log"
	"regexp"
	"strconv"
	"strings"

	"nook-agent/internal/execx"
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
	cur, err := l.currentRateMbps(ctx, iface)
	if err != nil {
		// 读当前限速失败(tc 瞬时错/超时): 不据此误判限速状态(否则可能误清或反复重建), 跳过本轮.
		log.Printf("[限速] 读取当前限速失败, 跳过本轮: %v", err)
		return
	}
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

// currentRateMbps 读本机 htb class 速率 (Mbps).
//   - 命令成功且有我们的 htb class → (mbps, nil)
//   - 命令成功但无 htb class → (0, nil): 确实无限速
//   - 命令执行失败(瞬时/超时) → (0, err): 由 Apply 跳过本轮, 不据此误判
func (l *Limiter) currentRateMbps(ctx context.Context, iface string) (int, error) {
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "tc", "class", "show", "dev", iface)
	defer cancel()
	out, err := cmd.CombinedOutput()
	if err != nil {
		return 0, fmt.Errorf("tc class show: %w (out=%s)", err, strings.TrimSpace(string(out)))
	}
	m := rateRe.FindStringSubmatch(string(out))
	if m == nil {
		return 0, nil // 命令成功但无 htb class → 无限速
	}
	val, perr := strconv.ParseFloat(m[1], 64)
	if perr != nil {
		return 0, nil
	}
	switch m[2] {
	case "K":
		val /= 1000
	case "G":
		val *= 1000
	}
	return int(val + 0.5), nil
}

// setRate 用 htb root + 单 default class 整形 egress; root 建一次, 速率改动落 class.
func (l *Limiter) setRate(ctx context.Context, iface string, mbps int) error {
	if err := l.ensureRootQdisc(ctx, iface); err != nil {
		return err
	}
	rate := strconv.Itoa(mbps) + "mbit"
	a := []string{"class", "replace", "dev", iface, "parent", "1:", "classid", "1:1", "htb", "rate", rate, "ceil", rate}
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "tc", a...)
	defer cancel()
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("tc %s: %w (out=%s)", strings.Join(a, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

// ensureRootQdisc 确保 htb root 存在: 已是我们的 htb 就不动; 否则把默认 root replace 成 htb.
func (l *Limiter) ensureRootQdisc(ctx context.Context, iface string) error {
	showCmd, showCancel := execx.Command(ctx, execx.DefaultTimeout, "tc", "qdisc", "show", "dev", iface)
	defer showCancel()
	out, err := showCmd.CombinedOutput()
	if err == nil && strings.Contains(string(out), "qdisc htb 1:") {
		return nil
	}
	a := []string{"qdisc", "replace", "dev", iface, "root", "handle", "1:", "htb", "default", "1"}
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "tc", a...)
	defer cancel()
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("tc %s: %w (out=%s)", strings.Join(a, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

// clear 删 root qdisc; 本来就没有视为幂等通过.
func (l *Limiter) clear(ctx context.Context, iface string) error {
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "tc", "qdisc", "del", "dev", iface, "root")
	defer cancel()
	out, err := cmd.CombinedOutput()
	if err != nil {
		low := strings.ToLower(string(out))
		if strings.Contains(low, "no such file") || strings.Contains(low, "invalid argument") {
			return nil
		}
		return fmt.Errorf("tc qdisc del: %w (out=%s)", err, strings.TrimSpace(string(out)))
	}
	return nil
}
