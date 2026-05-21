// nook-landing-agent: 落地机 agent. 当前是空壳 (socks5 接管放未来 sprint).
//
// 编译: go build -ldflags '-X main.Version=landing-0.7.0 -s -w' -o nook-landing-0.7.0-linux-amd64 ./cmd/landing
package main

import (
	"log"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	internalExec "nook-agent/internal/executor"
)

var Version = "landing-dev"

func main() {
	agentcore.Run(Version, registerLanding)
}

// 落地机角色注册器: 当前没有 socks5 collector / executor (留待后续 sprint).
// 此 agent 跟 frontline 区别仅在 binary 命名 + version 前缀, 共用心跳/NIC/poller/升级/改配置.
func registerLanding(_ *internalExec.Dispatcher, _ *config.Config, _ *client.Client) []agentcore.Goroutine {
	log.Printf("[landing] 启动 (空壳; socks5 接管待后续 sprint)")
	return nil
}
