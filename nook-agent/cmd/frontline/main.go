// nook-frontline-agent: 线路机 agent. 自带 xray executor + stats collector (启动自检 xray 是否真装了).
//
// 编译: go build -ldflags '-X main.Version=frontline-0.7.0 -s -w' -o nook-frontline-0.7.0-linux-amd64 ./cmd/frontline
package main

import (
	"context"
	"log"
	"os"
	"os/exec"
	"strings"

	"nook-agent/internal/agentcore"
	"nook-agent/internal/client"
	"nook-agent/internal/config"
	internalExec "nook-agent/internal/executor"
	"nook-agent/internal/xray"
)

// Version 编译时 ldflags 注入; 命名约定 "frontline-X.Y.Z".
var Version = "frontline-dev"

func main() {
	agentcore.Run(Version, registerFrontline)
}

// 角色注册器: 自检 xray 装没装, 装了才挂 xray 相关.
func registerFrontline(disp *internalExec.Dispatcher, cfg *config.Config, cli *client.Client) []agentcore.Goroutine {
	// yaml 里 xray.bin 优先; 缺省 /usr/local/bin/xray
	bin := cfg.Xray.Bin
	if bin == "" {
		bin = "/usr/local/bin/xray"
	}
	apiPort := cfg.Xray.APIPort
	if apiPort == 0 {
		apiPort = 10085
	}
	if !xrayInstalled(bin) {
		log.Printf("[frontline] xray 未装 (bin=%s 不存在 或 systemctl 不 active), 不挂 xray collector", bin)
		return nil
	}
	log.Printf("[frontline] xray 已装 (bin=%s apiPort=%d), 挂载 executor + stats collector", bin, apiPort)
	internalExec.NewXrayExecutor(bin, apiPort).Register(disp)
	xrayCli := xray.New(bin, apiPort)
	statsInterval := cfg.XrayStatsInterval()
	statsRep := xray.NewStatsReporter(cli, xrayCli, statsInterval)
	return []agentcore.Goroutine{
		func(ctx context.Context) { statsRep.Run(ctx) },
	}
}

// xrayInstalled 自检: 文件存在 即认为装了 (systemd unit name 可能多样, 不强求 active).
// 起停由其他 task 控制; 这里只决定"agent 要不要挂 xray collector".
func xrayInstalled(binPath string) bool {
	if _, err := os.Stat(binPath); err != nil {
		return false
	}
	out, err := exec.Command("systemctl", "is-active", "xray").CombinedOutput()
	state := strings.TrimSpace(string(out))
	log.Printf("[frontline] xray 自检: bin=%s 在; systemctl is-active xray = %s (err=%v)", binPath, state, err)
	return true
}
