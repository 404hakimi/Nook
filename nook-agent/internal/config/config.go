// Package config 加载 /etc/nook-agent/config.yml; 装机时由 backend 渲染.
package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 跟 config.yml 字段一一对齐; 装机时 backend 拼好整段写盘, agent 不补默认值.
type Config struct {
	Backend   BackendConfig      `yaml:"backend"`
	Heartbeat HeartbeatConfig    `yaml:"heartbeat"`
	NIC       NICConfig          `yaml:"nic"`
	Poller    PollerConfig       `yaml:"poller"`
	Xray      XrayConfig         `yaml:"xray"`
	Runtime   AgentRuntimeConfig `yaml:"runtime"`
}

type BackendConfig struct {
	APIURL         string `yaml:"api_url"`
	APIToken       string `yaml:"api_token"`
	TimeoutSeconds int    `yaml:"timeout_seconds"`
}

type HeartbeatConfig struct {
	IntervalSeconds int `yaml:"interval_seconds"`
}

// AgentRuntimeConfig: agent_upgrade 用; bin_path 是当前 binary 绝对路径.
type AgentRuntimeConfig struct {
	BinPath string `yaml:"bin_path"`
}

type NICConfig struct {
	IntervalSeconds int    `yaml:"interval_seconds"`
	// "auto" 自动用默认路由出口网卡, 显式给 eth0/ens5 覆盖.
	Interface string `yaml:"interface"`
}

type PollerConfig struct {
	IntervalSeconds int `yaml:"interval_seconds"`
}

// XrayConfig: frontline 角色必填; landing 角色整段缺省 (yaml 不出现 xray:), 解析后字段 zero value.
type XrayConfig struct {
	Bin                  string `yaml:"bin"`
	APIPort              int    `yaml:"api_port"`
	StatsIntervalSeconds int    `yaml:"stats_interval_seconds"`
}

// Load 读 config.yml + 强校验; 字段缺一报错, 不补默认.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读配置文件 %s 失败: %w", path, err)
	}
	c := &Config{}
	if err := yaml.Unmarshal(data, c); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}
	if err := c.validate(); err != nil {
		return nil, err
	}
	return c, nil
}

func (c *Config) validate() error {
	if c.Backend.APIURL == "" {
		return fmt.Errorf("backend.api_url 不能为空")
	}
	if c.Backend.APIToken == "" {
		return fmt.Errorf("backend.api_token 不能为空")
	}
	if c.Backend.TimeoutSeconds <= 0 {
		return fmt.Errorf("backend.timeout_seconds 必须 > 0")
	}
	if c.Heartbeat.IntervalSeconds <= 0 {
		return fmt.Errorf("heartbeat.interval_seconds 必须 > 0")
	}
	if c.NIC.IntervalSeconds <= 0 {
		return fmt.Errorf("nic.interval_seconds 必须 > 0")
	}
	if c.NIC.Interface == "" {
		return fmt.Errorf("nic.interface 不能为空")
	}
	if c.Poller.IntervalSeconds <= 0 {
		return fmt.Errorf("poller.interval_seconds 必须 > 0")
	}
	if c.Runtime.BinPath == "" {
		return fmt.Errorf("runtime.bin_path 不能为空")
	}
	return nil
}

func (c *Config) HeartbeatInterval() time.Duration {
	return time.Duration(c.Heartbeat.IntervalSeconds) * time.Second
}
func (c *Config) NICInterval() time.Duration { return time.Duration(c.NIC.IntervalSeconds) * time.Second }
func (c *Config) PollerInterval() time.Duration {
	return time.Duration(c.Poller.IntervalSeconds) * time.Second
}
func (c *Config) HTTPTimeout() time.Duration {
	return time.Duration(c.Backend.TimeoutSeconds) * time.Second
}
func (c *Config) XrayStatsInterval() time.Duration {
	return time.Duration(c.Xray.StatsIntervalSeconds) * time.Second
}
