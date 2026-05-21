// Package executor (config_reload_executor.go): 后台改配置 → 派 config_reload task → agent 写盘 + 自重启.
//
// 流程: agent 拉 task → atomic write yaml to configPath → 报 SUCCESS → 退出 (systemd Restart=always 拉起新进程).
//
// 这就是"热部署": agent 进程换一遍, xray/socks5 是独立进程不受影响.
package executor

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"
)

type ConfigReloadExecutor struct {
	configPath string // 例: /etc/nook-agent/config.yml
}

func NewConfigReloadExecutor(configPath string) *ConfigReloadExecutor {
	return &ConfigReloadExecutor{configPath: configPath}
}

func (e *ConfigReloadExecutor) Register(d *Dispatcher) {
	d.Register("config_reload", e.reload)
}

type configReloadPayload struct {
	Yaml string `json:"yaml"`
	MD5  string `json:"md5"` // backend 算的; agent 校验防内容损坏
}

func (e *ConfigReloadExecutor) reload(ctx context.Context, raw []byte) (string, error) {
	var p configReloadPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if p.Yaml == "" {
		return "", fmt.Errorf("yaml 不能为空")
	}
	// 校验 md5; 不一致说明 payload 在传输 / DB 里被破坏, 不应用
	gotMD5 := md5sum(p.Yaml)
	if p.MD5 != "" && p.MD5 != gotMD5 {
		return "", fmt.Errorf("md5 不匹配: 期望=%s 实际=%s", p.MD5, gotMD5)
	}
	dir := filepath.Dir(e.configPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", fmt.Errorf("mkdir %s 失败: %w", dir, err)
	}
	tmp := e.configPath + ".new"
	if err := os.WriteFile(tmp, []byte(p.Yaml), 0644); err != nil {
		return "", fmt.Errorf("写 %s 失败: %w", tmp, err)
	}
	if err := os.Rename(tmp, e.configPath); err != nil {
		_ = os.Remove(tmp)
		return "", fmt.Errorf("rename %s → %s 失败: %w", tmp, e.configPath, err)
	}
	log.Printf("[config-reload] 写盘 OK (%s, %d bytes, md5=%s), 1s 后自杀 systemd 拉起", e.configPath, len(p.Yaml), gotMD5)
	// 异步退出, 让本 task 的 SUCCESS 上报先飞出去. systemd RestartSec=10s 拉起.
	go func() {
		time.Sleep(1 * time.Second)
		os.Exit(0)
	}()
	return fmt.Sprintf(`{"configPath":%q,"bytes":%d,"md5":%q}`, e.configPath, len(p.Yaml), gotMD5), nil
}

func md5sum(s string) string {
	h := md5.Sum([]byte(s))
	return hex.EncodeToString(h[:])
}
