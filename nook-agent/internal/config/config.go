// Package config 加载 /etc/nook-agent/config.yml; 装机时由 backend 渲染.
package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 是 agent 启动时唯一可调参数集合. 字段全部带 yaml tag 跟 config.yml 对齐.
type Config struct {
	Backend  BackendConfig  `yaml:"backend"`
	Heartbeat HeartbeatConfig `yaml:"heartbeat"`
	NIC      NICConfig      `yaml:"nic"`
	Poller   PollerConfig   `yaml:"poller"`
	Xray     XrayConfig     `yaml:"xray"`
	Runtime  AgentRuntimeConfig `yaml:"runtime"`
}

type BackendConfig struct {
	// 服务端 API base URL, 如 https://nook.example.com 或 http://10.0.0.1:8080
	APIURL string `yaml:"api_url"`
	// X-Agent-Token Header 值; 装机时由 backend 生成下发
	APIToken string `yaml:"api_token"`
	// HTTP 客户端超时, 默认 30s
	TimeoutSeconds int `yaml:"timeout_seconds"`
}

type HeartbeatConfig struct {
	IntervalSeconds int `yaml:"interval_seconds"`
	// 已废弃: 用 ldflags -X main.Version 编译注入 (config 字段保留兼容).
	// 解析顺序: 编译注入 (优先) → config 显式值 → "dev".
	AgentVersion string `yaml:"agent_version"`
}

// AgentRuntimeConfig: 升级路径 / 自定义 binary 位置.
type AgentRuntimeConfig struct {
	// 二进制安装路径; 默认 /home/nook-agent/bin/nook-agent (跟 /home/socks5, /home/xray 同级);
	// 旧版本装到 /usr/local/bin/nook-agent 仍兼容 — 显式给绝对路径覆盖默认即可.
	// 升级时新 binary 写到此路径 + .new, 校验后 atomic rename.
	BinPath string `yaml:"bin_path"`
}

type NICConfig struct {
	// 5 min 默认; 0 表示不启用 NIC 流量上报 (e.g., 没装 vnstat 时)
	IntervalSeconds int `yaml:"interval_seconds"`
	// 网卡名, 默认 eth0
	Interface string `yaml:"interface"`
}

type PollerConfig struct {
	// 任务轮询间隔, 默认 30s
	IntervalSeconds int `yaml:"interval_seconds"`
}

// XrayConfig: enabled=false 时整个 xray executor + stats collector 不挂载 (没装 xray 的机器适用).
type XrayConfig struct {
	Enabled bool `yaml:"enabled"`
	// xray 二进制路径; 默认 /usr/local/bin/xray
	Bin string `yaml:"bin"`
	// xray 内置 api server 监听端口; 默认 10085
	APIPort int `yaml:"api_port"`
	// stats 上报间隔; 默认 300s (5min); 0 禁用
	StatsIntervalSeconds int `yaml:"stats_interval_seconds"`
}

// Load 读 config.yml 并补默认值.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读配置文件 %s 失败: %w", path, err)
	}
	c := &Config{}
	if err := yaml.Unmarshal(data, c); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}
	c.applyDefaults()
	if err := c.validate(); err != nil {
		return nil, err
	}
	return c, nil
}

func (c *Config) applyDefaults() {
	if c.Backend.TimeoutSeconds == 0 {
		c.Backend.TimeoutSeconds = 30
	}
	if c.Heartbeat.IntervalSeconds == 0 {
		c.Heartbeat.IntervalSeconds = 60
	}
	if c.Heartbeat.AgentVersion == "" {
		c.Heartbeat.AgentVersion = "0.1.0"
	}
	if c.NIC.IntervalSeconds == 0 {
		// NIC 上报默认 5min; 显式给 0 (而不是不填) 才禁用
		c.NIC.IntervalSeconds = 300
	}
	// nic.Interface 留空 / "auto" 都触发 /proc/net/route 探测默认路由网卡;
	// 显式指定才覆盖. 改 eth0 → ens5 这种, 在配置里手动写就行.
	if c.NIC.Interface == "" {
		c.NIC.Interface = "auto"
	}
	if c.Poller.IntervalSeconds == 0 {
		c.Poller.IntervalSeconds = 30
	}
	if c.Xray.Bin == "" {
		c.Xray.Bin = "/usr/local/bin/xray"
	}
	if c.Xray.APIPort == 0 {
		c.Xray.APIPort = 10085
	}
	if c.Xray.StatsIntervalSeconds == 0 {
		c.Xray.StatsIntervalSeconds = 300
	}
	if c.Runtime.BinPath == "" {
		// /home/nook-agent 跟 /home/socks5 / /home/xray 同级; 标准目录布局.
		// 旧装机用 /usr/local/bin/nook-agent 兼容继续工作 — config 显式给绝对路径覆盖默认即可.
		c.Runtime.BinPath = "/home/nook-agent/bin/nook-agent"
	}
}

// XrayStatsInterval 返回 xray stats 上报间隔 (time.Duration 版).
func (c *Config) XrayStatsInterval() time.Duration {
	return time.Duration(c.Xray.StatsIntervalSeconds) * time.Second
}

func (c *Config) validate() error {
	if c.Backend.APIURL == "" {
		return fmt.Errorf("backend.api_url 不能为空")
	}
	if c.Backend.APIToken == "" {
		return fmt.Errorf("backend.api_token 不能为空")
	}
	return nil
}

// HeartbeatInterval / NICInterval / PollerInterval / HTTPTimeout 提供时长版本省的调用方乘 time.Second.
func (c *Config) HeartbeatInterval() time.Duration { return time.Duration(c.Heartbeat.IntervalSeconds) * time.Second }
func (c *Config) NICInterval() time.Duration       { return time.Duration(c.NIC.IntervalSeconds) * time.Second }
func (c *Config) PollerInterval() time.Duration    { return time.Duration(c.Poller.IntervalSeconds) * time.Second }
func (c *Config) HTTPTimeout() time.Duration       { return time.Duration(c.Backend.TimeoutSeconds) * time.Second }
