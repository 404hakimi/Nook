// Package agentcore: 跨角色 (frontline / landing) 共享的 agent 启动骨架.
//
// cmd/frontline/main.go 和 cmd/landing/main.go 各自调 Run, 传入自己的 RoleRegister
// (挂自己特有的 collector). 共用部分 (心跳 / NIC) 在此完成.
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
	"nook-agent/internal/control"
	"nook-agent/internal/heartbeat"
	"nook-agent/internal/nic"
)

// Goroutine 一个长跑函数 (ctx 取消时退出); 角色注册器返一组要 wg.Add(1) + go 起来的.
type Goroutine func(ctx context.Context)

// RoleComponents: 角色注册器返回的组件 — 额外 goroutine + 可选的 nic 业务流量采样器.
type RoleComponents struct {
	Goroutines        []Goroutine
	NicBizSampler     func() (up, down *int64) // landing: 采样 nft socks5 业务上下行供 nic 上报; nil = nic 不报 biz
	XrayDeploy        control.XrayDeployFunc   // frontline: 控制接口 /xray/deploy 装机实现; nil = 不暴露该端点
	XrayCert          control.XrayCertFunc     // frontline: 控制接口 /xray/cert 证书续期写盘实现; nil = 不暴露该端点
	XrayStatusSampler func() bool              // frontline: 心跳采样 xray 是否运行; nil = 不报 (landing)
}

// RoleRegister: 角色自己挂额外 collector + 提供可选的业务流量采样器.
type RoleRegister func(cfg *config.Config, cli *client.Client) RoleComponents

// Run 是 main.go 的等价入口. version 形如 "frontline-0.9.0" / "landing-0.9.0".
func Run(version string, registerRole RoleRegister) {
	configPath := flag.String("c", "/etc/nook-agent/config.yml", "配置文件路径")
	showVersion := flag.Bool("v", false, "打印版本号后退出")
	flag.Parse()

	if *showVersion {
		_, _ = os.Stdout.WriteString(version + "\n")
		return
	}

	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	log.Printf("[主程序] nook-agent %s 启动, 配置文件=%s", version, *configPath)

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("[主程序] 配置加载失败: %v", err)
	}
	log.Printf("[主程序] 配置就绪: 后端=%s 心跳间隔=%d秒 流量上报间隔=%d秒(网卡=%s)",
		cfg.Backend.APIURL, int(cfg.HeartbeatInterval().Seconds()), int(cfg.NICInterval().Seconds()), cfg.NIC.Interface)

	cli := client.New(cfg.Backend.APIURL, cfg.Backend.APIToken, cfg.HTTPTimeout())

	// 角色注册器挂自己的 collector (e.g., frontline 挂 xray reconcile, landing 挂 tc 限速 + 业务流量计量)
	comp := registerRole(cfg, cli)

	hb := heartbeat.New(cli, cfg.HeartbeatInterval(), version, comp.XrayStatusSampler)
	nicRep := nic.New(cli, cfg.NICInterval(), cfg.NIC.Interface, comp.NicBizSampler)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var wg sync.WaitGroup
	wg.Add(2)
	go func() { defer wg.Done(); hb.Run(ctx) }()
	go func() { defer wg.Done(); nicRep.Run(ctx) }()
	// 控制接口: 后台 call agent 本地执行部署脚本 (port=0 不启用)
	if cfg.Control.Port > 0 {
		ctrl := control.New(cfg.Control.Port, cfg.Backend.APIToken, comp.XrayDeploy, comp.XrayCert)
		wg.Add(1)
		go func() { defer wg.Done(); ctrl.Run(ctx) }()
	}
	for _, g := range comp.Goroutines {
		wg.Add(1)
		g := g
		go func() { defer wg.Done(); g(ctx) }()
	}

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	log.Printf("[主程序] 收到退出信号, 关闭中...")
	cancel()
	wg.Wait()
	log.Printf("[主程序] 退出完成")
}
