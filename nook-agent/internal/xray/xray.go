// Package xray 调本地 xray api: adu/rmu/ado/rmo/statsquery.
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
)

// Client 调本地 xray api 的命令包装; 无状态.
type Client struct {
	xrayBin string // /usr/local/bin/xray 或 /home/xray/bin/xray
	apiPort int    // xray 内置 api server 端口, 一般 10085
}

func New(xrayBin string, apiPort int) *Client {
	return &Client{xrayBin: xrayBin, apiPort: apiPort}
}

func (c *Client) baseArgs(sub string) []string {
	return []string{"api", sub, fmt.Sprintf("--server=127.0.0.1:%d", c.apiPort)}
}

// AddUser: xray api adu < inbound JSON.
// inboundTag + user spec (uuid, email, level) 由 caller 拼好 inbound JSON 后传入.
func (c *Client) AddUser(ctx context.Context, inboundJSON string) error {
	args := c.baseArgs("adu")
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
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
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api rmu 失败: %w (stdout=%s)", err, out)
	}
	return nil
}

// AddOutbound: xray api ado < outbound JSON.
func (c *Client) AddOutbound(ctx context.Context, outboundJSON string) error {
	args := c.baseArgs("ado")
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	cmd.Stdin = strings.NewReader(outboundJSON)
	_, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api ado 失败: %w", err)
	}
	return nil
}

// RemoveOutbound: xray api rmo <outbound_tag>.
func (c *Client) RemoveOutbound(ctx context.Context, outboundTag string) error {
	args := append(c.baseArgs("rmo"), outboundTag)
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	_, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api rmo 失败: %w", err)
	}
	return nil
}

// AddRules: xray api adrules --append < routing JSON. routingJSON = {"routing":{"rules":[...]}}.
func (c *Client) AddRules(ctx context.Context, routingJSON string) error {
	args := append(c.baseArgs("adrules"), "--append")
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	cmd.Stdin = strings.NewReader(routingJSON)
	out, err := runCapture(cmd)
	if err != nil {
		return fmt.Errorf("xray api adrules 失败: %w (stdout=%s)", err, out)
	}
	return nil
}

// RemoveRule: xray api rmrules <ruleTag>. rule 不存在视为幂等通过.
func (c *Client) RemoveRule(ctx context.Context, ruleTag string) error {
	args := append(c.baseArgs("rmrules"), ruleTag)
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
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

// ListUsers: xray api inbounduser -tag=<inboundTag> → 该 inbound 现有 user email 集合 (reconcile 对账).
func (c *Client) ListUsers(ctx context.Context, inboundTag string) ([]string, error) {
	args := append(c.baseArgs("inbounduser"), "-tag="+inboundTag)
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	out, err := runCapture(cmd)
	if err != nil {
		return nil, fmt.Errorf("xray api inbounduser 失败: %w (stdout=%s)", err, out)
	}
	trimmed := strings.TrimSpace(out)
	if trimmed == "" {
		return nil, nil
	}
	var resp struct {
		Users []struct {
			Email string `json:"email"`
		} `json:"users"`
	}
	if err := json.Unmarshal([]byte(trimmed), &resp); err != nil {
		return nil, fmt.Errorf("解析 inbounduser 输出: %w (out=%s)", err, trimmed)
	}
	emails := make([]string, 0, len(resp.Users))
	for _, u := range resp.Users {
		if u.Email != "" {
			emails = append(emails, u.Email)
		}
	}
	return emails, nil
}

// ListOutboundTags: xray api lso → 所有 outbound tag (含静态; reconcile 按 out_ 前缀过滤业务出站).
func (c *Client) ListOutboundTags(ctx context.Context) ([]string, error) {
	cmd := exec.CommandContext(ctx, c.xrayBin, c.baseArgs("lso")...)
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
	cmd := exec.CommandContext(ctx, c.xrayBin, c.baseArgs("lsrules")...)
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

// UserStat 是 statsquery 单 user 双向流量快照.
type UserStat struct {
	Email    string `json:"email"`
	UpBytes  int64  `json:"upBytes"`
	DownBytes int64 `json:"downBytes"`
}

// StatsQuery 拉所有 user 流量; reset=true 顺手清零.
// 命令: xray api statsquery --pattern user>>> [--reset]
// 返回 JSON: { "stat": [{"name": "user>>>email>>>traffic>>>uplink|downlink", "value": "12345"}, ...] }
// 按 email 二次聚合上下行.
func (c *Client) StatsQuery(ctx context.Context, reset bool) ([]UserStat, error) {
	args := append(c.baseArgs("statsquery"), "--pattern", "user>>>")
	if reset {
		args = append(args, "--reset")
	}
	cmd := exec.CommandContext(ctx, c.xrayBin, args...)
	out, err := runCapture(cmd)
	if err != nil {
		return nil, fmt.Errorf("xray api statsquery 失败: %w", err)
	}
	return parseUserStats([]byte(out))
}

// statsResp 对应 xray api statsquery JSON; value 是数字, 0 值时字段会缺省
type statsResp struct {
	Stat []struct {
		Name  string `json:"name"`
		Value int64  `json:"value"`
	} `json:"stat"`
}

func parseUserStats(raw []byte) ([]UserStat, error) {
	raw = bytes.TrimSpace(raw)
	if len(raw) == 0 {
		return nil, nil
	}
	var resp statsResp
	if err := json.Unmarshal(raw, &resp); err != nil {
		return nil, fmt.Errorf("解析 statsquery 输出: %w", err)
	}
	agg := make(map[string]*UserStat)
	for _, s := range resp.Stat {
		// name 格式 user>>>EMAIL>>>traffic>>>{uplink|downlink}
		parts := strings.Split(s.Name, ">>>")
		if len(parts) != 4 || parts[0] != "user" || parts[2] != "traffic" {
			continue
		}
		email := parts[1]
		dir := parts[3]
		row, ok := agg[email]
		if !ok {
			row = &UserStat{Email: email}
			agg[email] = row
		}
		switch dir {
		case "uplink":
			row.UpBytes = s.Value
		case "downlink":
			row.DownBytes = s.Value
		}
	}
	out := make([]UserStat, 0, len(agg))
	for _, v := range agg {
		out = append(out, *v)
	}
	return out, nil
}

func runCapture(cmd *exec.Cmd) (string, error) {
	var buf bytes.Buffer
	cmd.Stdout = &buf
	cmd.Stderr = &buf
	err := cmd.Run()
	return buf.String(), err
}
