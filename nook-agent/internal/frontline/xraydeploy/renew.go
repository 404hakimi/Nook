package xraydeploy

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"time"
)

// CertRequest 跟后台 XrayCertPushRequest 字段对齐 (证书续期下发).
type CertRequest struct {
	ServerID       string `json:"serverId"`
	Domain         string `json:"domain"`
	TLSCertPem     string `json:"tlsCertPem"`
	TLSKeyPem      string `json:"tlsKeyPem"`
	TimeoutSeconds int    `json:"timeoutSeconds"`
}

// WriteCert 证书续期端点 (/xray/cert): 写后台重签后的 cert/key 到盘 + reload xray
// (xray 启动时读 cert 文件, 续期须 reload 才生效). 不重走装机, 比 /xray/deploy 轻; 流式回日志, 任一步失败返 error.
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
