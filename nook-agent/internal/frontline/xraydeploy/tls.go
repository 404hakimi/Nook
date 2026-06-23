package xraydeploy

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// ensureTLSCert 把后台签好下发的全链证书 / 私钥写到 p.certPath / p.keyPath.
// 证书签发 (Let's Encrypt + Cloudflare DNS-01) 全在后台完成, agent 不再直连 CF / 跑 acme.
func ensureTLSCert(out io.Writer, p paths, domain, certPem, keyPem string) error {
	if strings.TrimSpace(certPem) == "" || strings.TrimSpace(keyPem) == "" {
		return fmt.Errorf("绑域名 %s 但后台未下发证书 (tlsCertPem/tlsKeyPem 为空)", domain)
	}

	tlsDir := filepath.Dir(p.certPath)
	if err := os.MkdirAll(tlsDir, 0o700); err != nil {
		return fmt.Errorf("建 TLS 目录 %s 失败: %w", tlsDir, err)
	}
	_ = os.Chmod(tlsDir, 0o700)

	if err := os.WriteFile(p.certPath, []byte(certPem), 0o644); err != nil {
		return fmt.Errorf("写证书 %s 失败: %w", p.certPath, err)
	}
	if err := os.WriteFile(p.keyPath, []byte(keyPem), 0o600); err != nil {
		return fmt.Errorf("写私钥 %s 失败: %w", p.keyPath, err)
	}
	logf(out, "✔ 已写入后台下发的 TLS 证书 %s → %s", domain, p.certPath)
	return nil
}

// CertRequest 跟后台 XrayCertPushRequest 字段对齐 (证书续期下发).
type CertRequest struct {
	ServerID       string `json:"serverId"`
	Domain         string `json:"domain"`
	TLSCertPem     string `json:"tlsCertPem"`
	TLSKeyPem      string `json:"tlsKeyPem"`
	TimeoutSeconds int    `json:"timeoutSeconds"`
}

// WriteCert 证书续期: 写后台重签后的 cert/key 到盘 + reload xray (xray 启动时读 cert 文件, 续期须 reload 才生效).
// 不重走装机, 比 /xray/deploy 轻; 流式回日志, 任一步失败返 error.
func WriteCert(ctx context.Context, xrayBin string, body []byte, out io.Writer) error {
	var req CertRequest
	if err := json.Unmarshal(body, &req); err != nil {
		return fmt.Errorf("解析证书下发请求失败: %w", err)
	}
	timeout := time.Duration(req.TimeoutSeconds) * time.Second
	if timeout <= 0 {
		timeout = 2 * time.Minute
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	p := derivePaths(xrayBin)
	logf(out, "=== xray 证书续期写盘 domain=%s ===", req.Domain)
	if err := ensureTLSCert(out, p, req.Domain, req.TLSCertPem, req.TLSKeyPem); err != nil {
		return err
	}
	if err := sh(ctx, out, "systemctl", "reload-or-restart", "xray"); err != nil {
		return fmt.Errorf("reload xray 失败: %w", err)
	}
	logf(out, "✔ 证书已更新并 reload xray")
	return nil
}
