// Package socks5deploy 实现 landing 的 SOCKS5 (dante) 装机: 后台 POST /socks5/deploy 下发结构化配置,
// agent 用内置 Go 逻辑本地装机 (apt 装 dante / 写 danted.conf+PAM / systemd / 起服校验), 流式回日志.
//
// 取代退场的后台 bash 装机链路 (原 scripts/install/socks5-dante.sh.tmpl). 时区 / NTP 由 agent 启动 bootstrap
// 负责, 这里只做 dante 专属. 期望态全字段由后台从 socks5_install 表读出下发, 故服务器重置后可据 DB 重建.
//
// 文件划分:
//
//	socks5deploy.go — 入口 Deploy 编排 + Request 类型
//	render.go       — 安装目录 / danted.conf / PAM / systemd drop-in 写盘
//	service.go      — root 校验 / apt / 出网网卡探测 / htpasswd / systemctl 起服校验 / ufw / logrotate
//	exec.go         — shell 执行 + 流式日志辅助
package socks5deploy

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"time"
)

// 装机过程默认上限 (后台会带 timeoutSeconds; 这里兜底). dante 比 xray 轻 (无 binary 下载).
const defaultTimeout = 10 * time.Minute

// Request 跟后台 Socks5DeployRequest 字段对齐 (字段名 / json tag 须严格一致).
type Request struct {
	ServerID         string `json:"serverId"`
	Socks5Port       int    `json:"socks5Port"`
	Socks5Username   string `json:"socks5Username"`
	Socks5Password   string `json:"socks5Password"`
	LogLevel         string `json:"logLevel"`
	LogPath          string `json:"logPath"`
	InstallDir       string `json:"installDir"`
	ConfPath         string `json:"confPath"`
	PamFile          string `json:"pamFile"`
	PwdFile          string `json:"pwdFile"`
	SystemdUnit      string `json:"systemdUnit"`
	FirewallEnabled  bool   `json:"firewallEnabled"`
	LogRotateEnabled bool   `json:"logRotateEnabled"`
	SshPort          int    `json:"sshPort"`
	TimeoutSeconds   int    `json:"timeoutSeconds"`
}

// unit 返回 dante 的 systemd 单元名; 空则用 apt 包默认 danted.
func (r *Request) unit() string {
	if strings.TrimSpace(r.SystemdUnit) != "" {
		return r.SystemdUnit
	}
	return "danted"
}

// Deploy 解析下发配置, 跑 dante 装机各步, 进度流式写 out; 任一步失败返 error (调用方据此打 NOOK_RESULT=fail).
func Deploy(ctx context.Context, body []byte, out io.Writer) error {
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

	logf(out, "=== SOCKS5(dante) 部署 server=%s port=%d (装到 %s) ===", req.ServerID, req.Socks5Port, req.InstallDir)

	if err := preflight(out); err != nil {
		return err
	}
	if err := ensureDirs(out, &req); err != nil {
		return err
	}
	if err := installPackages(ctx, out); err != nil {
		return err
	}
	extIf := detectExtIface(out)
	if err := writePamFile(out, &req); err != nil {
		return err
	}
	if err := writeCredential(ctx, out, &req); err != nil {
		return err
	}
	if err := writeDanteConf(out, &req, extIf); err != nil {
		return err
	}
	if err := writeLogFile(&req); err != nil {
		return err
	}
	if err := writeDropin(out, &req); err != nil {
		return err
	}
	if err := startDante(ctx, out, &req); err != nil {
		return err
	}
	// UFW 仅在下发开启时配 (会放行 SSH + 控制口 44844 + SOCKS5)
	if req.FirewallEnabled {
		if err := ensureUfw(ctx, out, &req); err != nil {
			return err
		}
	}
	// 以下为 best-effort: 失败仅告警不阻断装机结果
	selfTestDial(ctx, out, &req)
	if req.LogRotateEnabled {
		ensureLogrotate(ctx, out, &req)
	}
	ensureJournaldCap(ctx, out)

	logf(out, "✔ SOCKS5(dante) 部署完成 (监听 0.0.0.0:%d)", req.Socks5Port)
	return nil
}
