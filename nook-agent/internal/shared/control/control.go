// Package control 暴露 agent 控制接口 (:44844): 后台 POST 下发结构化配置, agent 内置 Go 执行 + chunked 流式回 stdout.
// frontline: /xray/deploy 装机 + /xray/cert 续期证书; landing: /socks5/deploy 装 dante. 按角色提供的 func 是否非 nil 决定暴露哪些.
//
// 安全: 通道是明文 HTTP, 但 body 经后台 AES-256-GCM 加密 (key 由 agent_token 本地派生, token 不过线),
// agent「能成功解密」即鉴权 (见 crypto.go); 解密失败伪装 404, 不向扫描器暴露此口. 控制口 (44844) 对外开放,
// 安全靠"解密即鉴权 + 时间戳防重放"兜底; 如需进一步收紧可在 UFW 限源到后台固定出口 IP (当前未限).
package control

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"
)

// 请求体上限: 加密信封 (cert/key + base64 膨胀) 通常 KB 级, 留足余量又能挡住超大 body 打爆内存.
const maxBodyBytes = 4 << 20 // 4MiB

// XrayDeployFunc 由 frontline 角色提供: 解析下发配置 + 本地装 xray + 流式写 out; nil = 不暴露 /xray/deploy.
type XrayDeployFunc func(ctx context.Context, body []byte, out io.Writer) error

// XrayCertFunc 由 frontline 角色提供: 写后台续期下发的 cert/key + reload xray + 流式写 out; nil = 不暴露 /xray/cert.
type XrayCertFunc func(ctx context.Context, body []byte, out io.Writer) error

// Socks5DeployFunc 由 landing 角色提供: 解析下发配置 + 本地装 dante + 流式写 out; nil = 不暴露 /socks5/deploy.
type Socks5DeployFunc func(ctx context.Context, body []byte, out io.Writer) error

// Server 控制接口 HTTP server; 无状态.
type Server struct {
	port         int
	token        string
	xrayDeploy   XrayDeployFunc
	xrayCert     XrayCertFunc
	socks5Deploy Socks5DeployFunc
}

func New(port int, token string, xrayDeploy XrayDeployFunc, xrayCert XrayCertFunc, socks5Deploy Socks5DeployFunc) *Server {
	return &Server{port: port, token: token, xrayDeploy: xrayDeploy, xrayCert: xrayCert, socks5Deploy: socks5Deploy}
}

// Run 启动控制接口; ctx 取消时优雅关闭.
func (s *Server) Run(ctx context.Context) {
	mux := http.NewServeMux()
	if s.xrayDeploy != nil {
		mux.HandleFunc("/xray/deploy", s.handleXrayDeploy)
	}
	if s.xrayCert != nil {
		mux.HandleFunc("/xray/cert", s.handleXrayCert)
	}
	if s.socks5Deploy != nil {
		mux.HandleFunc("/socks5/deploy", s.handleSocks5Deploy)
	}
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

// handleXrayDeploy 后台通知 agent 本地装机: 解析下发配置 → 内置 Go 装机 → 流式回日志 + NOOK_RESULT.
func (s *Server) handleXrayDeploy(w http.ResponseWriter, r *http.Request) {
	s.handleStreamingJob(w, r, "/xray/deploy", s.xrayDeploy)
}

// handleXrayCert 后台续期: 推新 cert/key, agent 写盘 + reload xray → 流式回日志 + NOOK_RESULT.
func (s *Server) handleXrayCert(w http.ResponseWriter, r *http.Request) {
	s.handleStreamingJob(w, r, "/xray/cert", s.xrayCert)
}

// handleSocks5Deploy 后台通知 landing agent 本地装 dante: 解析下发配置 → 内置 Go 装机 → 流式回日志 + NOOK_RESULT.
func (s *Server) handleSocks5Deploy(w http.ResponseWriter, r *http.Request) {
	s.handleStreamingJob(w, r, "/socks5/deploy", s.socks5Deploy)
}

// handleStreamingJob 公共骨架: 强制加密载荷 → 解密(即鉴权) → chunked 流式跑 job → 行尾 NOOK_RESULT marker 判成败.
// 这些端点带 TLS 私钥, token 不再明文过线: 后台 AES-GCM 加密 body, 「能解密」即证明持 token.
func (s *Server) handleStreamingJob(w http.ResponseWriter, r *http.Request, name string,
	job func(ctx context.Context, body []byte, out io.Writer) error) {
	// 必须 POST + 带加密头; 否则伪装成"路径不存在"(与鉴权失败一致, 不向扫描器暴露这是控制口).
	if r.Method != http.MethodPost || r.Header.Get(encHeader) != encValue {
		http.NotFound(w, r)
		return
	}
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxBodyBytes))
	if err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}
	// 解密成功即鉴权; 失败 = token 不符 / 篡改 / 重放 → 同样伪装成 404, 不泄露.
	body, err = decryptControlBody(body, s.token)
	if err != nil {
		log.Printf("[控制接口] %s 解密失败 (鉴权/重放拒绝): %v", name, err)
		http.NotFound(w, r)
		return
	}
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	fw := &flushWriter{w: w, f: flusher}
	// chunked 200 已发无法改 status; 行尾用 NOOK_RESULT marker 让后台判成败.
	if err := job(r.Context(), body, fw); err != nil {
		fmt.Fprintf(fw, "\n[nook-agent] %s failed: %v\nNOOK_RESULT=fail\n", name, err)
		log.Printf("[控制接口] %s 失败: %v", name, err)
		return
	}
	fmt.Fprint(fw, "\nNOOK_RESULT=ok\n")
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
