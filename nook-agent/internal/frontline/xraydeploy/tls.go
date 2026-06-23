package xraydeploy

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// ensureTLSCert 把后台签好下发的全链证书 / 私钥写到 p.certPath / p.keyPath (cert 0644 / key 0600).
// 证书签发 (Let's Encrypt + Cloudflare DNS-01) 全在后台完成, agent 不再直连 CF / 跑 acme.
// 装机 (Deploy) 与续期 (WriteCert, 见 renew.go) 都复用本函数落盘.
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
