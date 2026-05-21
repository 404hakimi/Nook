// Package executor 任务分发. 起步只占位 (Sprint 1 接 xray-api / systemd-ctl 等真实执行器).
package executor

import (
	"context"
	"fmt"
)

// Dispatcher 是 task_type → executor 的注册表. 起步空, 收到未知 task_type 返 FAILED.
type Dispatcher struct {
	handlers map[string]Handler
}

// Handler 单个 task_type 的处理函数; payload 是后端发的 JSON 字节, 返回值是 resultPayload 字符串.
type Handler func(ctx context.Context, payload []byte) (string, error)

func New() *Dispatcher {
	d := &Dispatcher{handlers: make(map[string]Handler)}
	d.registerDefaults()
	return d
}

func (d *Dispatcher) Register(taskType string, h Handler) {
	d.handlers[taskType] = h
}

func (d *Dispatcher) Dispatch(ctx context.Context, taskType string, payload []byte) (string, error) {
	h, ok := d.handlers[taskType]
	if !ok {
		return "", fmt.Errorf("unsupported task_type: %s (agent 起步阶段, 待 Sprint 1+ 加 executor)", taskType)
	}
	return h(ctx, payload)
}

// registerDefaults 注册一个 ping 任务作为冒烟测试 (backend 派 type=ping → agent 返 "pong").
func (d *Dispatcher) registerDefaults() {
	d.Register("ping", func(ctx context.Context, payload []byte) (string, error) {
		return `{"pong":true}`, nil
	})
}
