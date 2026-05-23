// Package executor (upgrade_executor.go): 升级 agent 二进制本身 (agent_upgrade task).
//
// 流程: agent 拉到任务 → curl 下载新 binary 到 binPath+".new" → 校验 sha256 → mv 替换 binPath
// → 上报 SUCCESS → systemd Restart=always 拉起新版本 (本进程直接退出).
package executor

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

// UpgradeExecutor 升级 task handler.
type UpgradeExecutor struct {
	binPath     string
	authToken   string        // X-Agent-Token; download 时跟 backend /admin/agent-dist/bin 鉴权
	httpTimeout time.Duration // 下载 + 上报 task-result 的整体 timeout (跨境节点要够大, 从 backend.timeout_seconds 来)
}

func NewUpgradeExecutor(binPath, authToken string, httpTimeout time.Duration) *UpgradeExecutor {
	return &UpgradeExecutor{binPath: binPath, authToken: authToken, httpTimeout: httpTimeout}
}

func (e *UpgradeExecutor) Register(d *Dispatcher) {
	d.Register("agent_upgrade", e.upgrade)
}

type upgradePayload struct {
	// 新 binary 下载 URL (backend 暴露; 通常走 /admin/agent-dist/agent-bin-linux-amd64)
	URL string `json:"url"`
	// 期望 sha256 (hex, 64 char); 装机后校验
	Sha256 string `json:"sha256"`
	// 新版本号 (仅日志)
	Version string `json:"version"`
}

// upgrade 下载 → 校验 → 替换 → 退出 (systemd Restart=always 拉起新版本).
func (e *UpgradeExecutor) upgrade(ctx context.Context, raw []byte) (string, error) {
	var p upgradePayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if p.URL == "" || p.Sha256 == "" {
		return "", fmt.Errorf("url / sha256 不能为空")
	}
	newPath := e.binPath + ".new"
	// 1. 下载到临时文件 (带 X-Agent-Token, backend /admin/agent-dist/bin 鉴权用)
	if err := downloadFile(ctx, p.URL, newPath, e.authToken, e.httpTimeout); err != nil {
		return "", fmt.Errorf("下载失败: %w", err)
	}
	// 2. sha256 校验
	got, err := sha256Of(newPath)
	if err != nil {
		_ = os.Remove(newPath)
		return "", fmt.Errorf("计算 sha256 失败: %w", err)
	}
	if !strings.EqualFold(got, p.Sha256) {
		_ = os.Remove(newPath)
		return "", fmt.Errorf("sha256 不匹配: 期望=%s, 实际=%s", p.Sha256, got)
	}
	// 3. chmod +x + atomic rename
	if err := os.Chmod(newPath, 0755); err != nil {
		_ = os.Remove(newPath)
		return "", fmt.Errorf("chmod 失败: %w", err)
	}
	if err := os.Rename(newPath, e.binPath); err != nil {
		_ = os.Remove(newPath)
		return "", fmt.Errorf("替换 binary 失败: %w", err)
	}
	// 4. 上报 SUCCESS 后退出, systemd Restart=always 拉起新版本.
	// 等 httpTimeout + 2s 让 poller.runOne 同步 Post task-result 有充足时间跑完 —
	// 1s 太短, 跨境抖动时 Post 可能还没完成就被 os.Exit 强杀, backend 收不到 SUCCESS 会重派同一 task.
	go func() {
		time.Sleep(e.httpTimeout + 2*time.Second)
		os.Exit(0)
	}()
	return fmt.Sprintf(`{"version":"%s","binPath":"%s"}`, p.Version, e.binPath), nil
}

func downloadFile(ctx context.Context, url, dst, authToken string, timeout time.Duration) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	if authToken != "" {
		req.Header.Set("X-Agent-Token", authToken)
	}
	// 独立 client 带 timeout; http.DefaultClient 无 timeout, 卡死的下载永不返回会堵死 poller 队列
	httpCli := &http.Client{Timeout: timeout}
	resp, err := httpCli.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, resp.Body)
	return err
}

func sha256Of(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
