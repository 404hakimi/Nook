package socks5deploy

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"
)

// controlPort 是 agent 控制端口; 配 UFW 时必须放行, 否则 reset+enable 后后台再也连不上 agent 下发 (自锁).
const controlPort = 44844

// preflight: 必须 root (agent 以 root 跑才能 apt / systemctl / 写 /etc). 系统版本在 agent 上机脚本已校验, 此处不重校.
func preflight(out io.Writer) error {
	if os.Geteuid() != 0 {
		return fmt.Errorf("需 root 权限 (agent 应以 root 运行)")
	}
	logf(out, "✔ 权限 OK (root)")
	return nil
}

// installPackages 装 dante + htpasswd + pam_pwdfile + 探网卡/拨测依赖.
func installPackages(ctx context.Context, out io.Writer) error {
	logf(out, "→ apt 装 dante-server + apache2-utils + libpam-pwdfile...")
	env := []string{"DEBIAN_FRONTEND=noninteractive"}
	if err := shEnv(ctx, out, env, "apt-get", "update", "-qq"); err != nil {
		return fmt.Errorf("apt-get update 失败: %w", err)
	}
	if err := shEnv(ctx, out, env, "apt-get", "install", "-y", "-qq",
		"dante-server", "apache2-utils", "libpam-pwdfile", "iproute2", "curl"); err != nil {
		return fmt.Errorf("apt 装包失败: %w", err)
	}
	return nil
}

// detectExtIface 从 /proc/net/route 取默认路由出口网卡 (danted external); 探不到兜底环回防 conf 语法挂.
func detectExtIface(out io.Writer) string {
	if data, err := os.ReadFile("/proc/net/route"); err == nil {
		sc := bufio.NewScanner(strings.NewReader(string(data)))
		for first := true; sc.Scan(); first = false {
			if first {
				continue // 跳表头
			}
			f := strings.Fields(sc.Text())
			if len(f) >= 2 && f[1] == "00000000" {
				logf(out, "出网网卡: %s", f[0])
				return f[0]
			}
		}
	}
	logf(out, "⚠ 未探到默认路由网卡, 兜底 lo")
	return "lo"
}

// writeCredential 用 htpasswd 写 bcrypt 凭据; 密码经 stdin 传入 (不进 argv, 避免 ps 泄露).
func writeCredential(ctx context.Context, out io.Writer, req *Request) error {
	logf(out, "→ 写用户凭据 (htpasswd bcrypt): %s", req.Socks5Username)
	// -c 建文件(覆盖) -i 读 stdin -B bcrypt
	cmd := exec.CommandContext(ctx, "htpasswd", "-ciB", req.PwdFile, req.Socks5Username)
	cmd.Stdin = strings.NewReader(req.Socks5Password + "\n")
	cmd.Stdout, cmd.Stderr = out, out
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("htpasswd 写凭据失败: %w", err)
	}
	_ = os.Chmod(req.PwdFile, 0o600)
	return nil
}

// startDante daemon-reload + 按 autostart 设开机自启 + restart, 校验 active + 端口监听.
func startDante(ctx context.Context, out io.Writer, req *Request) error {
	unit := req.unit()
	logf(out, "→ 起服 (autostart=%v unit=%s)", req.AutostartEnabled, unit)
	_ = shAllowFail(ctx, out, "systemctl", "stop", unit)
	if err := sh(ctx, out, "systemctl", "daemon-reload"); err != nil {
		return fmt.Errorf("daemon-reload 失败: %w", err)
	}
	if req.AutostartEnabled {
		_ = shAllowFail(ctx, out, "systemctl", "enable", unit)
		logf(out, "✔ 开机自启已启用")
	} else {
		_ = shAllowFail(ctx, out, "systemctl", "disable", unit)
		logf(out, "  开机自启未启用 (按下发)")
	}
	if err := sh(ctx, out, "systemctl", "restart", unit); err != nil {
		return fmt.Errorf("启动 %s 失败: %w", unit, err)
	}
	time.Sleep(time.Second)
	if err := exec.CommandContext(ctx, "systemctl", "is-active", "--quiet", unit).Run(); err != nil {
		dumpJournal(ctx, out, unit)
		return fmt.Errorf("%s 未处于 active", unit)
	}
	if ss, err := capture(ctx, "ss", "-ltn"); err != nil || !strings.Contains(ss, fmt.Sprintf(":%d ", req.Socks5Port)) {
		dumpJournal(ctx, out, unit)
		return fmt.Errorf("SOCKS5 端口 %d 未监听", req.Socks5Port)
	}
	logf(out, "✔ dante 监听 0.0.0.0:%d (unit=%s)", req.Socks5Port, unit)
	return nil
}

func dumpJournal(ctx context.Context, out io.Writer, unit string) {
	if j, err := capture(ctx, "journalctl", "-u", unit, "-n", "30", "--no-pager"); err == nil {
		io.WriteString(out, j)
	}
}

// ensureUfw 配防火墙 (reset + 默认拒入放出 + 放行 SSH / 控制口 44844 / SOCKS5); 控制口必放否则 agent 自锁.
func ensureUfw(ctx context.Context, out io.Writer, req *Request) error {
	logf(out, "→ 配置 UFW (含放行控制口 %d, 防 agent 自锁)...", controlPort)
	_ = shEnv(ctx, out, []string{"DEBIAN_FRONTEND=noninteractive"}, "apt-get", "install", "-y", "-qq", "ufw")
	_ = shAllowFail(ctx, out, "ufw", "--force", "reset")
	_ = shAllowFail(ctx, out, "ufw", "default", "deny", "incoming")
	_ = shAllowFail(ctx, out, "ufw", "default", "allow", "outgoing")
	if req.SshPort > 0 {
		_ = shAllowFail(ctx, out, "ufw", "allow", fmt.Sprintf("%d/tcp", req.SshPort), "comment", "SSH")
	}
	_ = shAllowFail(ctx, out, "ufw", "allow", fmt.Sprintf("%d/tcp", controlPort), "comment", "nook agent control")
	_ = shAllowFail(ctx, out, "ufw", "allow", fmt.Sprintf("%d/tcp", req.Socks5Port), "comment", "SOCKS5")
	_ = shAllowFail(ctx, out, "ufw", "allow", fmt.Sprintf("%d/udp", req.Socks5Port), "comment", "SOCKS5")
	if err := sh(ctx, out, "ufw", "--force", "enable"); err != nil {
		return fmt.Errorf("ufw enable 失败: %w", err)
	}
	logf(out, "✔ UFW: SSH(%d) + 控制口(%d) + SOCKS5(%d) 已放行", req.SshPort, controlPort, req.Socks5Port)
	return nil
}

// selfTestDial 本机经 SOCKS5 拨号自检 (best-effort, 失败不阻断装机).
func selfTestDial(ctx context.Context, out io.Writer, req *Request) {
	logf(out, "→ 自检 SOCKS5 拨号 (best-effort)...")
	// 代理凭据经 -K - 从 stdin 传 (不进 argv/ps, 与 htpasswd 同口径); -s/--max-time 非敏感留 argv
	config := fmt.Sprintf("socks5 = \"127.0.0.1:%d\"\nproxy-user = \"%s:%s\"\nurl = \"https://ipinfo.io/ip\"\n",
		req.Socks5Port, req.Socks5Username, req.Socks5Password)
	cmd := exec.CommandContext(ctx, "curl", "-s", "--max-time", "10", "-K", "-")
	cmd.Stdin = strings.NewReader(config)
	o, err := cmd.Output()
	if err != nil || strings.TrimSpace(string(o)) == "" {
		logf(out, "⚠ 本地 SOCKS5 自检未通过 (服务已起, journalctl -u %s 查原因)", req.unit())
		return
	}
	logf(out, "✔ SOCKS5 自检通过, 出网 IP=%s", strings.TrimSpace(string(o)))
}

// ensureLogrotate 配 dante 日志轮转 (size 触发 + gzip + copytruncate, 0 中断, 适合低配机).
func ensureLogrotate(ctx context.Context, out io.Writer, req *Request) {
	logf(out, "→ 配置 logrotate (size 50M, 保留 5 份)...")
	if _, err := exec.LookPath("logrotate"); err != nil {
		_ = shEnv(ctx, out, []string{"DEBIAN_FRONTEND=noninteractive"}, "apt-get", "install", "-y", "-qq", "logrotate")
	}
	content := fmt.Sprintf("%s {\n    size 50M\n    rotate 5\n    compress\n    delaycompress\n    missingok\n    notifempty\n    copytruncate\n}\n", req.LogPath)
	if err := os.WriteFile("/etc/logrotate.d/dante", []byte(content), 0o644); err != nil {
		logf(out, "⚠ 写 logrotate 配置失败: %v (跳过)", err)
		return
	}
	if err := shAllowFail(ctx, out, "logrotate", "-f", "/etc/logrotate.d/dante"); err != nil {
		logf(out, "⚠ logrotate 校验有问题, 可手动 logrotate -f /etc/logrotate.d/dante 排查")
	}
}

// ensureJournaldCap 限 journald 容量 (50M ring buffer), 防业务 service 启停日志撑爆磁盘; best-effort.
func ensureJournaldCap(ctx context.Context, out io.Writer) {
	dir := "/etc/systemd/journald.conf.d"
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return
	}
	content := "[Journal]\nSystemMaxUse=50M\nSystemKeepFree=200M\nSystemMaxFileSize=10M\n"
	if err := os.WriteFile(dir+"/nook-cap.conf", []byte(content), 0o644); err != nil {
		return
	}
	_ = shAllowFail(ctx, out, "systemctl", "restart", "systemd-journald")
}
