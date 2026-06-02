// Package meter: 落地机业务流量计量.
//
// 拉 socks5 端口 → 在独立 nft 表 nook_meter 里计数"进出该端口的双向流量"(= 用户经落地机中转的业务量),
// 天然排除 agent↔后端 / 系统(DNS/NTP/apt) 流量。nic 上报时采样该计数器作为"业务流量"上报, backend 据此精确扣套餐。
//
// 设计要点:
//   - 独立 inet table nook_meter, 仅 counter 无 accept/drop → 不影响 UFW(在 ip/ip6 filter)。
//   - 重建判断以"内核里规则的真实端口"为准(不靠内存): 规则在且端口对就不动(保住累计值, agent 重启不误清→不漏算);
//     规则被删 / 端口变了才重建(自愈 + 端口切换), 重建 flush 归零由 backend "回退即重置" 兜底。
//   - agent 是 root, 直接 exec nft。
package meter

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os/exec"
	"regexp"
	"strconv"
	"time"

	"nook-agent/internal/client"
)

const portPath = "/api/agent/landing/socks5-port"

// 从 `nft list table` 文本里提 cnt_in 规则的 dport.
var dportRe = regexp.MustCompile(`dport (\d+)`)

// Meter 周期维护 nft 计数器(端口对账), 并提供 Sample 供 nic 上报采样. 无内存状态, 一切以内核为准.
type Meter struct {
	cli      *client.Client
	interval time.Duration
}

func New(cli *client.Client, interval time.Duration) *Meter {
	return &Meter{cli: cli, interval: interval}
}

// Run: 周期拉端口并确保 nft 计数器在位; ctx 取消退出.
func (m *Meter) Run(ctx context.Context) {
	if m.interval <= 0 {
		log.Printf("[meter] interval<=0, 不启动业务流量计量")
		return
	}
	log.Printf("[meter] 启动, interval=%v", m.interval)
	t := time.NewTicker(m.interval)
	defer t.Stop()
	m.refresh(ctx)
	for {
		select {
		case <-ctx.Done():
			log.Printf("[meter] 退出")
			return
		case <-t.C:
			m.refresh(ctx)
		}
	}
}

// refresh: 拉端口, 对照"内核里规则的真实端口"决定是否(重)建.
// 规则在且端口对 → 不动(保住累计值); 规则没了 / 端口不对 → 才重建(rebuild flush 归零, backend 回退兜底).
func (m *Meter) refresh(ctx context.Context) {
	var port int
	if err := m.cli.Get(portPath, &port); err != nil {
		log.Printf("[meter] 拉 socks5 端口失败, 跳过本轮: %v", err)
		return
	}
	if port <= 0 {
		log.Printf("[meter] 后端返回端口=%d (落地机未配 socks5?), 跳过", port)
		return
	}
	curPort, exists := m.currentRulePort()
	if exists && curPort == port {
		return // 规则在位且端口对, 不动(保住累计值; agent 重启走这条→不误清)
	}
	if err := m.rebuild(ctx, port); err != nil {
		log.Printf("[meter] 建 nft 计数器失败 port=%d: %v", port, err)
		return
	}
	log.Printf("[meter] nft 计数器(重)建就绪 port=%d (重建前 exists=%v port=%d)", port, exists, curPort)
}

// currentRulePort: 读内核 nook_meter 表里规则的实际端口; table 不存在 / 无规则返 (0,false) → 触发重建.
func (m *Meter) currentRulePort() (int, bool) {
	out, err := exec.Command("nft", "list", "table", "inet", "nook_meter").Output()
	if err != nil {
		return 0, false // table 不存在(或被删) → 需重建
	}
	match := dportRe.FindSubmatch(out)
	if match == nil {
		return 0, false
	}
	port, err := strconv.Atoi(string(match[1]))
	if err != nil {
		return 0, false
	}
	return port, true
}

// rebuild: 独立 table nook_meter, 进/出 socks5 端口各一个 counter(只数不拦); flush 重建会清零, 仅规则缺失/端口变时调.
func (m *Meter) rebuild(ctx context.Context, port int) error {
	script := fmt.Sprintf(`add table inet nook_meter
flush table inet nook_meter
table inet nook_meter {
	counter biz_in {}
	counter biz_out {}
	chain cnt_in {
		type filter hook input priority -300; policy accept;
		tcp dport %d counter name biz_in
	}
	chain cnt_out {
		type filter hook output priority -300; policy accept;
		tcp sport %d counter name biz_out
	}
}
`, port, port)
	cmd := exec.CommandContext(ctx, "nft", "-f", "-")
	cmd.Stdin = bytes.NewReader([]byte(script))
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("nft -f: %w (out=%s)", err, string(out))
	}
	return nil
}

// Sample: 读 nft 计数器(biz_in + biz_out 字节之和); table 不存在/读失败返 nil → nic 不报 biz, 后端回退整机 tx(refresh 会自愈).
func (m *Meter) Sample() *int64 {
	total, err := m.readCounters()
	if err != nil {
		return nil
	}
	return &total
}

// nft -j 输出结构 (只取 counter 的 bytes).
type nftJSON struct {
	Nftables []struct {
		Counter *struct {
			Bytes int64 `json:"bytes"`
		} `json:"counter"`
	} `json:"nftables"`
}

func (m *Meter) readCounters() (int64, error) {
	out, err := exec.Command("nft", "-j", "list", "counters", "table", "inet", "nook_meter").Output()
	if err != nil {
		return 0, fmt.Errorf("nft list counters: %w", err)
	}
	var parsed nftJSON
	if err := json.Unmarshal(out, &parsed); err != nil {
		return 0, fmt.Errorf("解析 nft json: %w", err)
	}
	var total int64
	for _, item := range parsed.Nftables {
		if item.Counter != nil {
			total += item.Counter.Bytes
		}
	}
	return total, nil
}
