package xraydeploy

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"
)

// ensureUfwPort 幂等放行共享 inbound 端口 (22 / 44844 由 agent 上机 bootstrap 已放行).
func ensureUfwPort(ctx context.Context, out io.Writer, port int) error {
	logf(out, "→ ufw 放行 inbound 端口 %d/tcp", port)
	if err := sh(ctx, out, "ufw", "allow", fmt.Sprintf("%d/tcp", port), "comment", "xray shared inbound"); err != nil {
		return fmt.Errorf("ufw 放行 %d 失败: %w", port, err)
	}
	return nil
}

// validateConfig 走 xray run -test 校验 config.json; 失败时把 xray 输出回流便于排查.
func validateConfig(ctx context.Context, out io.Writer, p paths) error {
	logf(out, "→ 校验 xray config...")
	cmd := exec.CommandContext(ctx, p.bin, "run", "-test", "-c", p.config)
	cmd.Env = append(os.Environ(), "XRAY_LOCATION_ASSET="+p.share)
	if b, err := cmd.CombinedOutput(); err != nil {
		out.Write(b)
		return fmt.Errorf("xray config 校验失败: %w", err)
	}
	logf(out, "✔ config 校验通过")
	return nil
}

// startXray daemon-reload + 开机自启 + restart (数据面一律自启).
func startXray(ctx context.Context, out io.Writer) error {
	if err := sh(ctx, out, "systemctl", "daemon-reload"); err != nil {
		return fmt.Errorf("daemon-reload 失败: %w", err)
	}
	// 数据面服务一律开机自启 (systemd 守护, 不依赖 agent 拉起)
	_ = shAllowFail(ctx, out, "systemctl", "enable", "xray")
	if err := sh(ctx, out, "systemctl", "restart", "xray"); err != nil {
		return fmt.Errorf("启动 xray 失败: %w", err)
	}
	time.Sleep(2 * time.Second)
	return nil
}

// verify 确认 xray active + (best-effort) api 端口监听.
func verify(ctx context.Context, out io.Writer, apiPort int) error {
	if err := exec.CommandContext(ctx, "systemctl", "is-active", "--quiet", "xray").Run(); err != nil {
		out2, _ := capture(ctx, "journalctl", "-u", "xray", "-n", "30", "--no-pager")
		io.WriteString(out, out2)
		return fmt.Errorf("xray 未处于 active")
	}
	// api 端口监听 best-effort: ss 缺失/异常只告警, 不阻断 (is-active 已是主判据).
	if ss, err := capture(ctx, "ss", "-ltn"); err == nil {
		if strings.Contains(ss, fmt.Sprintf("127.0.0.1:%d ", apiPort)) {
			logf(out, "✔ xray gRPC API 监听 127.0.0.1:%d", apiPort)
		} else {
			logf(out, "⚠ 未探到 api 端口 127.0.0.1:%d 监听 (xray 已 active, 继续)", apiPort)
		}
	}
	logf(out, "✔ xray 运行中")
	return nil
}
