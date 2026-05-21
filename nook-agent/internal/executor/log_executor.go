// Package executor (log_executor.go): 一键清日志 task (truncate_log).
//
// admin UI 触发 → backend 派 task → agent 对指定 path truncate -s 0 (保留 inode, 不重启服务).
package executor

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// LogExecutor truncate_log task handler.
type LogExecutor struct {
	// 白名单根目录前缀; 仅允许 truncate 这些目录下的文件, 避免远程被注入"清 /etc/passwd"
	allowedRoots []string
}

func NewLogExecutor(allowedRoots ...string) *LogExecutor {
	if len(allowedRoots) == 0 {
		allowedRoots = []string{"/var/log/", "/home/socks5/logs/", "/home/xray/logs/"}
	}
	return &LogExecutor{allowedRoots: allowedRoots}
}

func (e *LogExecutor) Register(d *Dispatcher) {
	d.Register("truncate_log", e.truncate)
}

type truncatePayload struct {
	// 要 truncate 的文件路径; 必须落在 allowedRoots 白名单内
	Paths []string `json:"paths"`
}

func (e *LogExecutor) truncate(ctx context.Context, raw []byte) (string, error) {
	var p truncatePayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if len(p.Paths) == 0 {
		return "", fmt.Errorf("paths 不能为空")
	}
	var summary []string
	for _, raw := range p.Paths {
		path := filepath.Clean(raw)
		if !e.isAllowed(path) {
			summary = append(summary, fmt.Sprintf("%s: 拒绝 (不在白名单)", path))
			continue
		}
		info, err := os.Stat(path)
		if err != nil {
			summary = append(summary, fmt.Sprintf("%s: %v", path, err))
			continue
		}
		oldSize := info.Size()
		if err := os.Truncate(path, 0); err != nil {
			summary = append(summary, fmt.Sprintf("%s: truncate 失败 %v", path, err))
			continue
		}
		summary = append(summary, fmt.Sprintf("%s: 释放 %d bytes", path, oldSize))
	}
	return fmt.Sprintf(`{"results":%q}`, strings.Join(summary, "; ")), nil
}

func (e *LogExecutor) isAllowed(path string) bool {
	for _, root := range e.allowedRoots {
		if strings.HasPrefix(path, root) {
			return true
		}
	}
	return false
}
