// Package executor (xray_executor.go): xray 相关 task_type 的 Handler 实现.
package executor

import (
	"context"
	"encoding/json"
	"fmt"

	"nook-agent/internal/xray"
)

// XrayExecutor 把 task_type → xray 命令的映射注册到 Dispatcher.
type XrayExecutor struct {
	xray *xray.Client
}

func NewXrayExecutor(xrayBin string, apiPort int) *XrayExecutor {
	return &XrayExecutor{xray: xray.New(xrayBin, apiPort)}
}

// Register 把所有 xray task_type 挂到 Dispatcher.
func (e *XrayExecutor) Register(d *Dispatcher) {
	d.Register("xray_provision_user", e.provisionUser)
	d.Register("xray_remove_user", e.removeUser)
	d.Register("xray_update_outbound", e.updateOutbound)
}

// provisionUser 接 backend 派的 inbound JSON 完整体; 直接 adu.
//
// payload 形如 (跟后端 ClientOpExecutor 现有 build 的 JSON 对齐):
//
//	{
//	  "inboundJson": "{ \"tag\": \"<inboundTag>\", \"users\": [{...}] }"
//	}
type provisionUserPayload struct {
	InboundJSON string `json:"inboundJson"`
}

func (e *XrayExecutor) provisionUser(ctx context.Context, raw []byte) (string, error) {
	var p provisionUserPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if p.InboundJSON == "" {
		return "", fmt.Errorf("inboundJson 不能为空")
	}
	if err := e.xray.AddUser(ctx, p.InboundJSON); err != nil {
		return "", err
	}
	return `{"ok":true}`, nil
}

type removeUserPayload struct {
	InboundTag string `json:"inboundTag"`
	Email      string `json:"email"`
}

func (e *XrayExecutor) removeUser(ctx context.Context, raw []byte) (string, error) {
	var p removeUserPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if p.InboundTag == "" || p.Email == "" {
		return "", fmt.Errorf("inboundTag / email 不能为空")
	}
	if err := e.xray.RemoveUser(ctx, p.InboundTag, p.Email); err != nil {
		return "", err
	}
	return `{"ok":true}`, nil
}

// updateOutbound: 切换 outbound (换 IP); 先 rmo 旧 outboundTag 再 ado 新 outbound JSON.
type updateOutboundPayload struct {
	OldOutboundTag string `json:"oldOutboundTag"`
	NewOutboundJSON string `json:"newOutboundJson"`
}

func (e *XrayExecutor) updateOutbound(ctx context.Context, raw []byte) (string, error) {
	var p updateOutboundPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return "", fmt.Errorf("payload 反序列化失败: %w", err)
	}
	if p.NewOutboundJSON == "" {
		return "", fmt.Errorf("newOutboundJson 不能为空")
	}
	// rmo 失败不一定致命 (可能 tag 不存在), 仅记日志后继续 ado
	if p.OldOutboundTag != "" {
		_ = e.xray.RemoveOutbound(ctx, p.OldOutboundTag)
	}
	if err := e.xray.AddOutbound(ctx, p.NewOutboundJSON); err != nil {
		return "", err
	}
	return `{"ok":true}`, nil
}
