package socks5deploy

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
)

func logf(out io.Writer, format string, a ...any) {
	fmt.Fprintf(out, "[nook-agent] "+format+"\n", a...)
}

// sh 跑命令, stdout/stderr 流式写 out; 失败返 error.
func sh(ctx context.Context, out io.Writer, name string, args ...string) error {
	fmt.Fprintf(out, "[nook-agent] $ %s %s\n", name, strings.Join(args, " "))
	cmd := exec.CommandContext(ctx, name, args...)
	cmd.Stdout = out
	cmd.Stderr = out
	return cmd.Run()
}

// shEnv 同 sh 但追加环境变量 (如 apt 的 DEBIAN_FRONTEND).
func shEnv(ctx context.Context, out io.Writer, env []string, name string, args ...string) error {
	fmt.Fprintf(out, "[nook-agent] $ %s %s\n", name, strings.Join(args, " "))
	cmd := exec.CommandContext(ctx, name, args...)
	cmd.Env = append(os.Environ(), env...)
	cmd.Stdout = out
	cmd.Stderr = out
	return cmd.Run()
}

// shAllowFail 同 sh 但忽略错误 (如 systemctl stop 一个未起的服务).
func shAllowFail(ctx context.Context, out io.Writer, name string, args ...string) error {
	cmd := exec.CommandContext(ctx, name, args...)
	cmd.Stdout = out
	cmd.Stderr = out
	return cmd.Run()
}

func capture(ctx context.Context, name string, args ...string) (string, error) {
	o, err := exec.CommandContext(ctx, name, args...).Output()
	return string(o), err
}
