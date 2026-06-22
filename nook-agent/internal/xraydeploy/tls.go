package xraydeploy

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// ensureTLSCert 保证 p.certPath/p.keyPath 对 domain 有效: 现有 cert 匹配且剩余 >7 天则复用 (避开
// Let's Encrypt 周 5 张配额), 否则 acme.sh + Cloudflare DNS-01 签发. cfToken 仅入 env, 不进日志.
func ensureTLSCert(ctx context.Context, out io.Writer, p paths, domain, cfToken string) error {
	tlsDir := filepath.Dir(p.certPath)
	if err := os.MkdirAll(tlsDir, 0o700); err != nil {
		return fmt.Errorf("建 TLS 目录 %s 失败: %w", tlsDir, err)
	}
	_ = os.Chmod(tlsDir, 0o700)

	if certValidFor(ctx, p.certPath, p.keyPath, domain) {
		logf(out, "✔ 已有 TLS 证书匹配 %s 且剩余 >7 天, 跳过签发", domain)
		return nil
	}
	if strings.TrimSpace(cfToken) == "" {
		return fmt.Errorf("需签发 TLS 证书但 cfApiToken 为空 (填 Token 或预置 cert/key 到 %s)", tlsDir)
	}

	home := os.Getenv("HOME")
	if home == "" {
		home = "/root"
	}
	acme := filepath.Join(home, ".acme.sh", "acme.sh")
	if _, err := os.Stat(acme); err != nil {
		logf(out, "→ 安装 acme.sh ...")
		if err := sh(ctx, out, "sh", "-c", "curl -fsSL https://get.acme.sh | sh -s email=admin@"+domain); err != nil {
			return fmt.Errorf("安装 acme.sh 失败: %w", err)
		}
	}
	_ = shAllowFail(ctx, out, acme, "--set-default-ca", "--server", "letsencrypt")

	logf(out, "→ 为 %s 签发 TLS (acme.sh + Cloudflare DNS-01)...", domain)
	if err := shEnv(ctx, out, []string{"CF_Token=" + cfToken}, acme,
		"--issue", "--dns", "dns_cf", "-d", domain, "--keylength", "2048", "--force"); err != nil {
		return fmt.Errorf("acme.sh 签发失败 (检查 CF Token Zone:Read+DNS:Edit 及 DNS 解析): %w", err)
	}
	if err := sh(ctx, out, acme, "--install-cert", "-d", domain,
		"--key-file", p.keyPath,
		"--fullchain-file", p.certPath,
		"--reloadcmd", "systemctl reload-or-restart xray 2>/dev/null || true"); err != nil {
		return fmt.Errorf("acme.sh 安装证书失败: %w", err)
	}
	logf(out, "✔ TLS 证书 → %s", p.certPath)
	return nil
}

// certValidFor 现有 cert/key 存在 且 匹配 domain 且 剩余 >7 天.
func certValidFor(ctx context.Context, certPath, keyPath, domain string) bool {
	if _, err := os.Stat(certPath); err != nil {
		return false
	}
	if _, err := os.Stat(keyPath); err != nil {
		return false
	}
	// -checkend 退出码 0 = 剩余 > 7 天
	if err := exec.CommandContext(ctx, "openssl", "x509", "-checkend", "604800", "-noout", "-in", certPath).Run(); err != nil {
		return false
	}
	// -checkhost 两侧退出码都 0, 必须抓 "does match" 输出
	o, err := capture(ctx, "openssl", "x509", "-checkhost", domain, "-noout", "-in", certPath)
	if err != nil {
		return false
	}
	return strings.Contains(o, "does match")
}
