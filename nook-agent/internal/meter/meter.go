// Package meter: 落地机业务流量计量. 期望 socks5 端口由 landing 循环从后端拉来后调 Ensure, 本包不自拉不自循环.
//
// 在独立 nft 表 nook_meter 里计数"进出 socks5 端口的双向流量"(= 用户经落地机中转的业务量),
// 天然排除 agent↔后端 / 系统(DNS/NTP/apt) 流量。nic 上报时调 Sample 采样, backend 据此精确扣套餐。
//
// 设计要点:
//   - 独立 inet table nook_meter, 仅 counter 无 accept/drop → 不影响 UFW(在 ip/ip6 filter)。
//   - Ensure 以"内核里规则的真实端口"为准: 规则在且端口对就不动(保住累计值, agent 重启不误清→不漏算);
//     规则被删 / 端口变了才重建(自愈 + 端口切换), 重建 flush 归零由 backend "回退即重置" 兜底。
//   - agent 是 root, 直接 exec nft。
package meter

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"regexp"
	"strconv"
	"strings"

	"nook-agent/internal/execx"
)

// 从 `nft list table` 文本里提 cnt_in 规则的 dport.
var dportRe = regexp.MustCompile(`dport (\d+)`)

// Meter 维护 nft 业务流量计数器, 并提供 SampleUpDown 供 nic 上报采样. 无内存状态, 一切以内核为准.
type Meter struct{}

func New() *Meter {
	return &Meter{}
}

// Ensure: 确保 nft 计数器在位且端口为 port (0=落地机未配 socks5, 跳过).
// 以内核实际规则为准: 规则在且端口对 → 不动(保住累计值); 规则没了 / 端口不对 → 才重建(flush 归零, backend 回退兜底).
func (m *Meter) Ensure(ctx context.Context, port int) {
	if port <= 0 {
		return
	}
	curPort, present, err := m.currentRulePort(ctx)
	if err != nil {
		// 读规则失败(nft 瞬时错/超时): 不能据此当"表不存在"去 rebuild —— rebuild 会 flush 清零累计值.
		// 跳过本轮, 保住既有计数器; 下轮重试.
		log.Printf("[计量] 读取计数器规则失败, 跳过本轮 (不重建, 保住累计值): %v", err)
		return
	}
	if present && curPort == port {
		return // 规则在位且端口对, 不动(agent 重启走这条→不误清)
	}
	if err := m.rebuild(ctx, port); err != nil {
		log.Printf("[计量] 业务流量计数器创建失败 端口=%d: %v", port, err)
		return
	}
	log.Printf("[计量] 业务流量计数器已就绪 端口=%d (重建前: 存在=%v 端口=%d)", port, present, curPort)
}

// currentRulePort: 读内核 nook_meter 表里规则的实际端口.
//   - 表存在且有 dport 规则 → (port, true, nil)
//   - 确认表/规则不存在 → (0, false, nil): 正常缺失, 交给 Ensure 重建
//   - nft 命令执行失败(瞬时/超时/nft 缺失) → (0, false, err): Ensure 跳过本轮, 不 rebuild, 不清零
func (m *Meter) currentRulePort(ctx context.Context) (int, bool, error) {
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "nft", "list", "table", "inet", "nook_meter")
	defer cancel()
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	out, err := cmd.Output()
	if err != nil {
		if tableAbsent(stderr.String(), err) {
			return 0, false, nil // 表确实不存在(或被删) → 需重建
		}
		return 0, false, fmt.Errorf("nft list table: %w (stderr=%s)", err, strings.TrimSpace(stderr.String()))
	}
	match := dportRe.FindSubmatch(out)
	if match == nil {
		return 0, false, nil // 表在但无我们的规则 → 需重建
	}
	port, perr := strconv.Atoi(string(match[1]))
	if perr != nil {
		return 0, false, nil // 端口解析不出 → 当作需重建
	}
	return port, true, nil
}

// tableAbsent: 判断 nft 的失败是不是"表不存在"(正常缺失, 应重建), 而非命令执行失败(瞬时/超时, 应跳过).
func tableAbsent(stderr string, err error) bool {
	if errors.Is(err, context.DeadlineExceeded) {
		return false // 超时不是"表不存在"
	}
	low := strings.ToLower(stderr)
	return strings.Contains(low, "no such file") || strings.Contains(low, "does not exist")
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
	cmd, cancel := execx.Command(ctx, execx.DefaultTimeout, "nft", "-f", "-")
	defer cancel()
	cmd.Stdin = bytes.NewReader([]byte(script))
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("nft -f: %w (out=%s)", err, string(out))
	}
	return nil
}

// SampleUpDown: 读 nft 计数器, 上行=biz_in(进 socks5 端口=客户端→代理), 下行=biz_out(出 socks5 端口=代理→客户端);
// table 不存在/读失败返 (nil,nil) → nic 不报 biz, 后端本轮不更新业务流量.
func (m *Meter) SampleUpDown() (up, down *int64) {
	in, out, err := m.readCounters()
	if err != nil {
		return nil, nil
	}
	return &in, &out
}

// nft -j 输出结构 (取 counter 的 name + bytes).
type nftJSON struct {
	Nftables []struct {
		Counter *struct {
			Name  string `json:"name"`
			Bytes int64  `json:"bytes"`
		} `json:"counter"`
	} `json:"nftables"`
}

// readCounters: 分别读 biz_in(上行) / biz_out(下行) 累计字节.
func (m *Meter) readCounters() (in, out int64, err error) {
	// SampleUpDown 由 nic 采样器(无 ctx 形参)调, 故用 Background; 单次超时仍生效, 卡死即本轮不报 biz.
	cmd, cancel := execx.Command(context.Background(), execx.DefaultTimeout, "nft", "-j", "list", "counters", "table", "inet", "nook_meter")
	defer cancel()
	raw, err := cmd.Output()
	if err != nil {
		return 0, 0, fmt.Errorf("nft list counters: %w", err)
	}
	var parsed nftJSON
	if err := json.Unmarshal(raw, &parsed); err != nil {
		return 0, 0, fmt.Errorf("解析 nft json: %w", err)
	}
	for _, item := range parsed.Nftables {
		if item.Counter == nil {
			continue
		}
		switch item.Counter.Name {
		case "biz_in":
			in = item.Counter.Bytes
		case "biz_out":
			out = item.Counter.Bytes
		}
	}
	return in, out, nil
}
