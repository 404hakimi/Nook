// Package xraydeploy 实现 frontline 的 xray 装机: 后台 POST /xray/deploy 下发结构化配置,
// agent 用内置 Go 逻辑本地装机(下载 xray / 写 config.json 入站 / systemd / 校验起服), 流式回日志.
//
// 取代退场的后台 bash 装机链路 (原 scripts/modules/50-xray.sh.tmpl 等). 机器级 bootstrap
// (apt 依赖 / 时区 / ufw 基础规则) 由 agent 上机时的 SSH 脚本负责, 这里只做 xray 专属 + 补端口.
//
// 覆盖: 非 TLS (vless-reality / vmess-ws-plain) 与 绑域名 TLS (vmess-ws-tls; acme.sh + Cloudflare DNS-01).
//
// 文件划分:
//
//	xraydeploy.go — 入口 Deploy 编排 + Request/paths 类型
//	binary.go     — xray binary 下载/版本/解压
//	tls.go        — acme.sh + Cloudflare DNS-01 证书
//	render.go     — config.json + systemd unit 渲染
//	service.go    — ufw / systemctl / 校验起服
//	exec.go       — shell 执行 + 流式日志辅助
package xraydeploy

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"path/filepath"
	"strings"
	"time"
)

// 装机过程的默认上限 (后台会带 timeoutSeconds=1200; 这里兜底).
const defaultTimeout = 20 * time.Minute

// 这些是 agent 自有约定 (与后台 inboundConfigJson / XrayInstallDefaults 一致): 日志级别 / 重启策略.
const (
	logLevel      = "warning"
	restartPolicy = "on-failure"
)

// Request 跟后台 XrayDeployRequest 字段对齐.
type Request struct {
	ServerID          string `json:"serverId"`
	XrayVersion       string `json:"xrayVersion"`
	ForceReinstall    bool   `json:"forceReinstall"`
	EnableOnBoot      bool   `json:"enableOnBoot"`
	InstallUfw        bool   `json:"installUfw"`
	SetTimezone       bool   `json:"setTimezone"`
	LogRotate         bool   `json:"logRotate"`
	SharedInboundPort int    `json:"sharedInboundPort"`
	InboundConfigJSON string `json:"inboundConfigJson"`
	Domain            string `json:"domain"`
	CfAPIToken        string `json:"cfApiToken"`
	TimeoutSeconds    int    `json:"timeoutSeconds"`
}

// paths: 由 xray binary 路径派生其余布局 (= XrayInstallDefaults 约定: /home/xray/...).
type paths struct {
	bin, binDir, installDir, config, share, logDir, certPath, keyPath, unit string
}

func derivePaths(xrayBin string) paths {
	binDir := filepath.Dir(xrayBin)    // /home/xray/bin
	installDir := filepath.Dir(binDir) // /home/xray
	return paths{
		bin:        xrayBin,
		binDir:     binDir,
		installDir: installDir,
		config:     filepath.Join(installDir, "config.json"),
		share:      binDir,
		logDir:     installDir,
		certPath:   filepath.Join(installDir, "tls", "cert.pem"),
		keyPath:    filepath.Join(installDir, "tls", "key.pem"),
		unit:       "/etc/systemd/system/xray.service",
	}
}

// Deploy 解析下发配置, 跑装机各步, 进度流式写 out; 任一步失败返 error (调用方据此打 NOOK_RESULT=fail).
func Deploy(ctx context.Context, xrayBin string, apiPort int, body []byte, out io.Writer) error {
	var req Request
	if err := json.Unmarshal(body, &req); err != nil {
		return fmt.Errorf("解析装机请求失败: %w", err)
	}
	timeout := time.Duration(req.TimeoutSeconds) * time.Second
	if timeout <= 0 {
		timeout = defaultTimeout
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	p := derivePaths(xrayBin)
	logf(out, "=== xray 部署 server=%s 目标版本=%s (装到 %s) ===", req.ServerID, req.XrayVersion, p.installDir)

	useTLS := strings.TrimSpace(req.Domain) != ""

	if req.InstallUfw {
		if err := ensureUfwPort(ctx, out, req.SharedInboundPort); err != nil {
			return err
		}
	}
	if useTLS {
		// 绑域名: 先签/复用 TLS 证书到 p.certPath/p.keyPath (config.json 的 tlsSettings 引用它).
		if err := ensureTLSCert(ctx, out, p, req.Domain, req.CfAPIToken); err != nil {
			return err
		}
	}
	if err := ensureXrayBinary(ctx, out, p, req.XrayVersion, req.ForceReinstall); err != nil {
		return err
	}
	if err := writeConfig(out, p, apiPort, req.InboundConfigJSON); err != nil {
		return err
	}
	if err := writeSystemdUnit(out, p); err != nil {
		return err
	}
	if err := validateConfig(ctx, out, p); err != nil {
		return err
	}
	if err := startXray(ctx, out, req.EnableOnBoot); err != nil {
		return err
	}
	if err := verify(ctx, out, apiPort); err != nil {
		return err
	}
	logf(out, "✔ xray 部署完成")
	return nil
}
