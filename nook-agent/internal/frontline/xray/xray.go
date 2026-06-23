// Package xray 调本地 xray api: adu/rmu/ado/rmo/adrules/rmrules + inbounduser/lso/lsrules (reconcile 对账读本地态).
//
// xray 二进制路径 + api server 端口由 config 给; agent 跟 xray 在同台机器, 走 127.0.0.1:apiPort.
// 命令格式参考 nook-module-node/framework/xray/cli/Xray*Cli.java 现有实现.
package xray

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"strings"

	"nook-agent/internal/shared/execx"
)

// Client 调本地 xray api 的命令包装; 无状态.
type Client struct {
	xrayBin string // /usr/local/bin/xray 或 /home/xray/bin/xray
	apiPort int    // xray 内置 api server 端口, 一般 10085
}

func New(xrayBin string, apiPort int) *Client {
	return &Client{xrayBin: xrayBin, apiPort: apiPort}
}

// Bin 返回 xray 程序路径; reconcile 据此探 binary 是否就绪.
func (c *Client) Bin() string { return c.xrayBin }

func (c *Client) baseArgs(sub string) []string {
	return []string{"api", sub, fmt.Sprintf("--server=127.0.0.1:%d", c.apiPort)}
}

// command 构造带单次超时的 xray api 命令 (本地 127.0.0.1 gRPC, 正常亚秒级); 调用方须 defer 返回的 cancel.
// 防止某次 xray api 调用卡死把整条 reconcile loop 永久阻塞.
func (c *Client) command(ctx context.Context, args ...string) (*exec.Cmd, context.CancelFunc) {
	return execx.Command(ctx, execx.DefaultTimeout, c.xrayBin, args...)
}

// AddUser: xray api adu < inbound JSON.
// inboundTag + user spec (uuid, email, level) 由 caller 拼好 inbound JSON 后传入.
func (c *Client) AddUser(ctx context.Context, inboundJSON string) error {
	// adu 无 stdin 自动 fallback (跟 ado/adrules 不同), 必须显式传 stdin: 否则静默 "Added 0 user(s)".
	args := append(c.baseArgs("adu"), "stdin:")
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	cmd.Stdin = strings.NewReader(inboundJSON)
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api adu 失败: %w (stdout=%s)", err, out)
	}
	// 防 silent fail: stdout 必须含 "Added 1 user(s)"
	if !strings.Contains(out, "Added 1 user(s)") {
		return fmt.Errorf("adu 静默失败, stdout=%s", out)
	}
	return nil
}

// RemoveUser: xray api rmu -tag=<tag> <email>.
func (c *Client) RemoveUser(ctx context.Context, inboundTag, email string) error {
	args := append(c.baseArgs("rmu"), "-tag="+inboundTag, email)
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api rmu 失败: %w (stdout=%s)", err, out)
	}
	return nil
}

// AddOutbound: xray api ado < outbound JSON; 校验 stdout 含 "adding: <tag>" 防 exit 0 静默加 0 条.
func (c *Client) AddOutbound(ctx context.Context, tag, outboundJSON string) error {
	args := append(c.baseArgs("ado"), "stdin:")
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	cmd.Stdin = strings.NewReader(outboundJSON)
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api ado 失败: %w (stdout=%s)", err, out)
	}
	if !strings.Contains(out, "adding: "+tag) {
		return fmt.Errorf("ado 静默失败 (exit 0 但 stdout 无 'adding: %s'): %s", tag, out)
	}
	return nil
}

// RemoveOutbound: xray api rmo <outbound_tag>.
func (c *Client) RemoveOutbound(ctx context.Context, outboundTag string) error {
	args := append(c.baseArgs("rmo"), outboundTag)
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	_, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api rmo 失败: %w", err)
	}
	return nil
}

// AddRules: xray api adrules --append < routing JSON; 加完用 ListRuleTags 确认 ruleTag 出现, 防 exit 0 静默没加.
func (c *Client) AddRules(ctx context.Context, ruleTag, routingJSON string) error {
	args := append(c.baseArgs("adrules"), "--append", "stdin:")
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	cmd.Stdin = strings.NewReader(routingJSON)
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api adrules 失败: %w (stdout=%s)", err, out)
	}
	tags, lerr := c.ListRuleTags(ctx)
	if lerr != nil {
		return fmt.Errorf("adrules 后确认 ruleTag 失败: %w", lerr)
	}
	for _, t := range tags {
		if t == ruleTag {
			return nil
		}
	}
	return fmt.Errorf("adrules 静默失败: ruleTag %s 未出现在远端", ruleTag)
}

// RemoveRule: xray api rmrules <ruleTag>. rule 不存在视为幂等通过.
func (c *Client) RemoveRule(ctx context.Context, ruleTag string) error {
	args := append(c.baseArgs("rmrules"), ruleTag)
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	out, err := runCapture(cmd)
	if err != nil {
		low := strings.ToLower(out)
		if strings.Contains(low, "not found") || strings.Contains(low, "no such") {
			return nil
		}
		return fmt.Errorf("xray api rmrules 失败: %w (stdout=%s)", err, out)
	}
	return nil
}

// ListUsers: xray api inbounduser -tag=<inboundTag> → 该 inbound 现有 user 的 email→uuid 映射 (reconcile 对账).
// account.id 即 user UUID (vmess/vless), reconcile 用它判断 UUID 是否轮换.
func (c *Client) ListUsers(ctx context.Context, inboundTag string) (map[string]string, error) {
	args := append(c.baseArgs("inbounduser"), "-tag="+inboundTag)
	cmd, cancel := c.command(ctx, args...)
	defer cancel()
	out, err := runCapture(cmd)
	if err != nil {
		return nil, fmt.Errorf("xray api inbounduser 失败: %w (stdout=%s)", err, out)
	}
	trimmed := strings.TrimSpace(out)
	if trimmed == "" {
		return map[string]string{}, nil
	}
	var resp struct {
		Users []struct {
			Email   string `json:"email"`
			Account struct {
				ID string `json:"id"`
			} `json:"account"`
		} `json:"users"`
	}
	if err := json.Unmarshal([]byte(trimmed), &resp); err != nil {
		return nil, fmt.Errorf("解析 inbounduser 输出: %w (out=%s)", err, trimmed)
	}
	users := make(map[string]string, len(resp.Users))
	for _, u := range resp.Users {
		if u.Email != "" {
			users[u.Email] = u.Account.ID
		}
	}
	return users, nil
}

// ListOutboundTags: xray api lso → 所有 outbound tag (含静态; reconcile 按 out_ 前缀过滤业务出站).
func (c *Client) ListOutboundTags(ctx context.Context) ([]string, error) {
	cmd, cancel := c.command(ctx, c.baseArgs("lso")...)
	defer cancel()
	out, err := runCapture(cmd)
	if err != nil {
		return nil, fmt.Errorf("xray api lso 失败: %w (stdout=%s)", err, out)
	}
	trimmed := strings.TrimSpace(out)
	if trimmed == "" {
		return nil, nil
	}
	var resp struct {
		Outbounds []struct {
			Tag string `json:"tag"`
		} `json:"outbounds"`
	}
	if err := json.Unmarshal([]byte(trimmed), &resp); err != nil {
		return nil, fmt.Errorf("解析 lso 输出: %w (out=%s)", err, trimmed)
	}
	tags := make([]string, 0, len(resp.Outbounds))
	for _, o := range resp.Outbounds {
		if o.Tag != "" {
			tags = append(tags, o.Tag)
		}
	}
	return tags, nil
}

// ListRuleTags: xray api lsrules → 所有 routing rule 的 ruleTag (无 ruleTag 的内置 api 规则自然跳过).
func (c *Client) ListRuleTags(ctx context.Context) ([]string, error) {
	cmd, cancel := c.command(ctx, c.baseArgs("lsrules")...)
	defer cancel()
	out, err := runCapture(cmd)
	if err != nil {
		return nil, fmt.Errorf("xray api lsrules 失败: %w (stdout=%s)", err, out)
	}
	trimmed := strings.TrimSpace(out)
	if trimmed == "" {
		return nil, nil
	}
	var resp struct {
		Rules []struct {
			RuleTag string `json:"ruleTag"`
		} `json:"rules"`
	}
	if err := json.Unmarshal([]byte(trimmed), &resp); err != nil {
		return nil, fmt.Errorf("解析 lsrules 输出: %w (out=%s)", err, trimmed)
	}
	tags := make([]string, 0, len(resp.Rules))
	for _, r := range resp.Rules {
		if r.RuleTag != "" {
			tags = append(tags, r.RuleTag)
		}
	}
	return tags, nil
}

func runCapture(cmd *exec.Cmd) (string, error) {
	var buf bytes.Buffer
	cmd.Stdout = &buf
	cmd.Stderr = &buf
	err := cmd.Run()
	return buf.String(), err
}
