// Package agentcore: 跨角色 (frontline / landing) 共享的 agent 启动骨架.
//
// cmd/frontline/main.go 和 cmd/landing/main.go 各自调 Run, 传入自己的 RoleRegister
// (挂自己特有的 executor + collector). 共用部分 (心跳/NIC/poller/升级/清日志/改配置) 在此完成.
package agentcore

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"nook-agent/internal/client"
	"nook-agent/internal/config"
	"nook-agent/internal/executor"
	"nook-agent/internal/heartbeat"
	"nook-agent/internal/nic"
	"nook-agent/internal/poller"
)

// Goroutine 一个长跑函数 (ctx 取消时退出); 角色注册器返一组要 wg.Add(1) + go 起来的.
type Goroutine func(ctx context.Context)

// RoleRegister: 角色自己挂额外 executor + collector; 返回额外的 goroutine.
type RoleRegister func(exec *executor.Dispatcher, cfg *config.Config, cli *client.Client) []Goroutine

// Run 是 main.go 的等价入口. version 形如 "frontline-0.7.0" / "landing-0.7.0".
func Run(version string, registerRole RoleRegister) {
	configPath := flag.String("c", "/etc/nook-agent/config.yml", "配置文件路径")
	showVersion := flag.Bool("v", false, "打印版本号后退出")
	flag.Parse()

	if *showVersion {
		_, _ = os.Stdout.WriteString(version + "\n")
		return
	}

	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	log.Printf("[main] nook-agent %s 启动, configPath=%s", version, *configPath)

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("[main] 配置加载失败: %v", err)
	}
	log.Printf("[main] 配置 OK: apiURL=%s hb=%v nic=%v(iface=%s) poll=%v",
		cfg.Backend.APIURL, cfg.HeartbeatInterval(), cfg.NICInterval(), cfg.NIC.Interface,
		cfg.PollerInterval())

	cli := client.New(cfg.Backend.APIURL, cfg.Backend.APIToken, cfg.HTTPTimeout())
	exec := executor.New()

	// 共用 executor (跟角色无关)
	binPath, err := os.Executable()
	if err != nil || binPath == "" {
		binPath = cfg.Runtime.BinPath
		log.Printf("[main] os.Executable 失败, 用 config 兜底 bin_path=%s", binPath)
	}
	log.Printf("[main] upgrade 目标路径 (自动探测): %s", binPath)
	executor.NewUpgradeExecutor(binPath, cfg.Backend.APIToken).Register(exec)
	executor.NewLogExecutor().Register(exec)
	executor.NewConfigReloadExecutor(*configPath).Register(exec)

	// 角色注册器挂自己的 (e.g., frontline 挂 xray)
	extraGoroutines := registerRole(exec, cfg, cli)

	hb := heartbeat.New(cli, cfg.HeartbeatInterval(), version)
	nicRep := nic.New(cli, cfg.NICInterval(), cfg.NIC.Interface)
	pol := poller.New(cli, cfg.PollerInterval(), exec)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var wg sync.WaitGroup
	wg.Add(3)
	go func() { defer wg.Done(); hb.Run(ctx) }()
	go func() { defer wg.Done(); nicRep.Run(ctx) }()
	go func() { defer wg.Done(); pol.Run(ctx) }()
	for _, g := range extraGoroutines {
		wg.Add(1)
		g := g
		go func() { defer wg.Done(); g(ctx) }()
	}

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	log.Printf("[main] 收到退出信号, 关闭中...")
	cancel()
	wg.Wait()
	log.Printf("[main] 退出完成")
}
