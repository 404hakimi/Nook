// Package execx 给所有外部命令(nft / tc / vnstat / xray api)套一个"单次超时".
//
// 背景: agent 的采样/对账 loop 都是单 goroutine 串行 exec 本地命令. 原来用裸 exec.Command
// 或把长生命周期的 loop ctx 传给 CommandContext —— 两者都没有"单次"上界: 一旦某个子进程
// 卡死(磁盘 IO stall / netlink 锁 / 库锁), 该 loop 会永久阻塞、再不上报, 而心跳是另一条
// goroutine 仍正常 → 后端看机器"在线"却收不到流量, 静默丢计费. 这里统一给每次调用派生一个
// 独立的 timeout ctx, 卡死即超时跳过本轮, 下轮重试.
//
// 注: 对处于不可中断睡眠(D 状态, 如卡死的存储)的子进程, SIGKILL 也无法立即收割, Wait 仍可能
// 阻塞 —— 这是内核层面无解的极端情形; WaitDelay 兜住"进程已退但管道被孙进程继承"这类常见情形.
package execx

import (
	"context"
	"os/exec"
	"time"
)

// DefaultTimeout 本地探测/采样类命令的单次上限. nft/tc/vnstat/xray-api 正常都是亚秒级,
// 给足余量(忙时/大 ruleset)又不至于把真卡死的调用一直拖着.
const DefaultTimeout = 15 * time.Second

// killGrace 超时 cancel(默认 SIGKILL) 后, 再等这么久让进程退出并关闭管道; 到点强制让 Wait 返回.
const killGrace = 5 * time.Second

// Command 构造一个带"单次超时"的 *exec.Cmd. 调用方照常设 Stdin/Stdout 后 Run/Output/CombinedOutput,
// 并 defer 返回的 cancel 释放计时器.
//
// parent 传所在 loop 的 ctx 时, 既受单次超时约束、也能在 agent 退出(ctx 取消)时一并中断;
// 拿不到 loop ctx 的调用点(如 nic.bizSampler 无 ctx 形参)传 context.Background() 即可, 单次超时照样生效.
func Command(parent context.Context, timeout time.Duration, name string, args ...string) (*exec.Cmd, context.CancelFunc) {
	if parent == nil {
		parent = context.Background()
	}
	if timeout <= 0 {
		timeout = DefaultTimeout
	}
	ctx, cancel := context.WithTimeout(parent, timeout)
	cmd := exec.CommandContext(ctx, name, args...)
	cmd.WaitDelay = killGrace
	return cmd, cancel
}
