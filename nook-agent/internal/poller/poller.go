// Package poller 每 30s 拉 PENDING 任务 → 派给 executor 执行 → 回报结果.
package poller

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"nook-agent/internal/client"
	"nook-agent/internal/executor"
)

type Poller struct {
	cli      *client.Client
	interval time.Duration
	exec     *executor.Dispatcher
}

func New(cli *client.Client, interval time.Duration, exec *executor.Dispatcher) *Poller {
	return &Poller{cli: cli, interval: interval, exec: exec}
}

// Task 跟后端 AgentTaskRespVO 对齐.
type Task struct {
	ID          string          `json:"id"`
	TaskType    string          `json:"taskType"`
	TaskPayload string          `json:"taskPayload"` // backend 序列化的 JSON 字符串, agent 端二次反序列化
	Payload     json.RawMessage `json:"-"`           // 解析后的 payload (executor 用)
}

func (p *Poller) Run(ctx context.Context) {
	ticker := time.NewTicker(p.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("[poller] 退出")
			return
		case <-ticker.C:
			p.tick(ctx)
		}
	}
}

func (p *Poller) tick(ctx context.Context) {
	var tasks []Task
	if err := p.cli.Get("/api/agent/tasks?limit=10", &tasks); err != nil {
		log.Printf("[poller] 拉任务失败: %v", err)
		return
	}
	if len(tasks) == 0 {
		return
	}
	log.Printf("[poller] 拉到 %d 个任务", len(tasks))
	for _, t := range tasks {
		p.runOne(ctx, t)
	}
}

type resultReq struct {
	TaskID        string `json:"taskId"`
	Status        string `json:"status"` // SUCCESS / FAILED
	ResultPayload string `json:"resultPayload,omitempty"`
}

func (p *Poller) runOne(ctx context.Context, t Task) {
	log.Printf("[poller] 执行任务 id=%s type=%s", t.ID, t.TaskType)
	status := "SUCCESS"
	resultPayload := ""
	res, err := p.exec.Dispatch(ctx, t.TaskType, []byte(t.TaskPayload))
	if err != nil {
		status = "FAILED"
		resultPayload = err.Error()
		log.Printf("[poller] 任务 %s 失败: %v", t.ID, err)
	} else {
		resultPayload = res
	}
	if err := p.cli.Post("/api/agent/task-result", resultReq{
		TaskID:        t.ID,
		Status:        status,
		ResultPayload: resultPayload,
	}, nil); err != nil {
		log.Printf("[poller] 回报结果失败 taskId=%s: %v", t.ID, err)
	}
}
