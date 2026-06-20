// Package control 暴露 agent 控制接口: 后台 POST /execute 下发脚本文本, agent 本地 bash 执行 + chunked 流式回 stdout.
//
// 鉴权复用 backend.api_token (后台 call 时带 X-Agent-Token). agent 纯出站之外仅此一个入站口, 装机时 UFW 应只放行后台出口 IP.
package control

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"time"
)

const tokenHeader = "X-Agent-Token"

// 脚本未带超时时的兜底上限.
const defaultTimeout = 30 * time.Minute

// 请求体上限: 部署脚本通常 KB 级, 留足余量又能挡住超大 body 把内存/磁盘(写 /tmp)打爆.
const maxBodyBytes = 4 << 20 // 4MiB

// Server 控制接口 HTTP server; 无状态.
type Server struct {
	port  int
	token string
}

func New(port int, token string) *Server {
	return &Server{port: port, token: token}
}

// execRequest 跟后台 AgentControlClient 下发体对齐.
type execRequest struct {
	Script         string `json:"script"`
	TimeoutSeconds int    `json:"timeoutSeconds"`
}

// Run 启动控制接口; ctx 取消时优雅关闭.
func (s *Server) Run(ctx context.Context) {
	mux := http.NewServeMux()
	mux.HandleFunc("/execute", s.handleExecute)
	srv := &http.Server{Addr: fmt.Sprintf(":%d", s.port), Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go func() {
		<-ctx.Done()
		shutCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutCtx)
	}()
	log.Printf("[控制接口] 监听 :%d", s.port)
	// ctx 取消触发 Shutdown → ListenAndServe 返回 ErrServerClosed, 属正常优雅退出.
	// 其余错误(端口被占/权限/非法地址)是启动失败: 不能只打一行日志让进程带病活着(控制面静默黑洞),
	// 直接退出, 交给 systemd Restart=always 重试.
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("[控制接口] 监听失败, 进程退出待 systemd 重启: %v", err)
	}
}

func (s *Server) handleExecute(w http.ResponseWriter, r *http.Request) {
	// 鉴权失败 / 非法方法一律伪装成"此路径不存在": 与空 http server 访问未知路径的响应字节完全一致,
	// 让扫描器从 44844 探不出这是 Nook 控制口. 只有"正确 token + POST"才真正放行执行.
	if r.Method != http.MethodPost || !tokenOK(r.Header.Get(tokenHeader), s.token) {
		http.NotFound(w, r)
		return
	}
	// 到这里的都是带对 token 的后台调用: body 限大小, 解析/内容错误照常报真错(便于后台排查).
	var req execRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBodyBytes)).Decode(&req); err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}
	if req.Script == "" {
		http.Error(w, "script empty", http.StatusBadRequest)
		return
	}
	s.runScript(w, r, req)
}

// tokenOK 常量时间比较 token, 避免按匹配前缀长度变化的计时侧信道泄露 token.
func tokenOK(got, want string) bool {
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

// runScript 写脚本临时文件 + bash 执行, stdout/stderr 实时 flush 回响应流.
func (s *Server) runScript(w http.ResponseWriter, r *http.Request, req execRequest) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	tmp, err := os.CreateTemp("", "nook-deploy-*.sh")
	if err != nil {
		http.Error(w, "create temp failed: "+err.Error(), http.StatusInternalServerError)
		return
	}
	defer os.Remove(tmp.Name())
	if _, err := tmp.WriteString(req.Script); err != nil {
		http.Error(w, "write temp failed: "+err.Error(), http.StatusInternalServerError)
		return
	}
	_ = tmp.Close()

	timeout := time.Duration(req.TimeoutSeconds) * time.Second
	if timeout <= 0 {
		timeout = defaultTimeout
	}
	ctx, cancel := context.WithTimeout(r.Context(), timeout)
	defer cancel()

	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(http.StatusOK)

	fw := &flushWriter{w: w, f: flusher}
	cmd := exec.CommandContext(ctx, "bash", tmp.Name())
	cmd.Stdout = fw
	cmd.Stderr = fw
	// chunked 200 已发无法改 status; 行尾用机器可读 marker (NOOK_RESULT) 让后台判定成败, 并区分超时
	err = cmd.Run()
	if errors.Is(ctx.Err(), context.DeadlineExceeded) {
		fmt.Fprintf(fw, "\n[nook-agent] exec timeout (>%ds)\nNOOK_RESULT=fail\n", int(timeout.Seconds()))
		log.Printf("[控制接口] /execute 超时 (>%ds)", int(timeout.Seconds()))
		return
	}
	if err != nil {
		fmt.Fprintf(fw, "\n[nook-agent] exec failed: %v\nNOOK_RESULT=fail\n", err)
		log.Printf("[控制接口] /execute 失败: %v", err)
		return
	}
	fmt.Fprint(fw, "\n[nook-agent] exec ok\nNOOK_RESULT=ok\n")
}

// flushWriter 每次写后立即 flush, 让后台实时收到 stdout.
type flushWriter struct {
	w http.ResponseWriter
	f http.Flusher
}

func (fw *flushWriter) Write(p []byte) (int, error) {
	n, err := fw.w.Write(p)
	fw.f.Flush()
	return n, err
}
