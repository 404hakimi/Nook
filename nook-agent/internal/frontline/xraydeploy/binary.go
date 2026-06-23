package xraydeploy

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

// ensureXrayBinary 按版本幂等装 xray binary: 版本一致且非强制则跳过; 否则下载解压到 bin 目录.
func ensureXrayBinary(ctx context.Context, out io.Writer, p paths, version string, force bool) error {
	zipName, err := arch()
	if err != nil {
		return err
	}
	need := true
	if !force {
		if cur, ok := installedVersion(ctx, p.bin); ok {
			if version != "latest" && version != "" && version == "v"+cur {
				logf(out, "→ xray 已是目标版本 v%s, 跳过下载", cur)
				need = false
			} else {
				logf(out, "→ xray 现 v%s, 目标 %s → 重装", cur, version)
			}
		}
	} else {
		logf(out, "→ 强制重装 xray")
	}

	// 不论是否下载都先停服, 保证后面 daemon-reload + 新 config 生效.
	_ = shAllowFail(ctx, out, "systemctl", "stop", "xray")
	for _, d := range []string{p.binDir, filepath.Dir(p.config), p.share, p.logDir} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			return fmt.Errorf("建目录 %s 失败: %w", d, err)
		}
	}
	if !need {
		return nil
	}

	url := downloadURL(version, zipName)
	logf(out, "→ 下载 xray (%s, %s)\n  %s", version, runtime.GOARCH, url)
	tmpZip, err := os.CreateTemp("", "xray-*.zip")
	if err != nil {
		return fmt.Errorf("建临时文件失败: %w", err)
	}
	defer os.Remove(tmpZip.Name())
	if err := downloadTo(ctx, url, tmpZip); err != nil {
		_ = tmpZip.Close()
		return fmt.Errorf("下载失败 %s: %w", url, err)
	}
	_ = tmpZip.Close()

	// 备份旧 binary (若有)
	if cur, ok := installedVersion(ctx, p.bin); ok {
		bak := filepath.Join(p.binDir, "xray.bak.v"+cur)
		if data, e := os.ReadFile(p.bin); e == nil {
			_ = os.WriteFile(bak, data, 0o755)
			logf(out, "  备份旧 binary v%s → %s", cur, bak)
		}
	}
	if err := unzipXray(tmpZip.Name(), p.bin, p.share); err != nil {
		return fmt.Errorf("解压失败: %w", err)
	}
	if err := os.Chmod(p.bin, 0o755); err != nil {
		return fmt.Errorf("chmod xray 失败: %w", err)
	}
	logf(out, "✔ xray binary: %s", p.bin)
	return nil
}

func arch() (string, error) {
	switch runtime.GOARCH {
	case "amd64":
		return "Xray-linux-64.zip", nil
	case "arm64":
		return "Xray-linux-arm64-v8a.zip", nil
	case "arm":
		return "Xray-linux-arm32-v7a.zip", nil
	default:
		return "", fmt.Errorf("不支持的架构: %s", runtime.GOARCH)
	}
}

func downloadURL(version, zipName string) string {
	if version == "latest" || version == "" {
		return "https://github.com/XTLS/Xray-core/releases/latest/download/" + zipName
	}
	return "https://github.com/XTLS/Xray-core/releases/download/" + version + "/" + zipName
}

// installedVersion 取已装 xray 版本号 (不含 v 前缀); 未装/取不到返 ("", false).
func installedVersion(ctx context.Context, bin string) (string, bool) {
	if _, err := os.Stat(bin); err != nil {
		return "", false
	}
	o, err := capture(ctx, bin, "version")
	if err != nil {
		return "", false
	}
	// 首行形如 "Xray 1.8.4 (...)"; 取第二段.
	fields := strings.Fields(strings.SplitN(o, "\n", 2)[0])
	if len(fields) < 2 {
		return "", false
	}
	return fields[1], true
}

func downloadTo(ctx context.Context, url string, dst *os.File) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	_, err = io.Copy(dst, resp.Body)
	return err
}

// unzipXray 从 zip 解出 xray binary (→ binPath) 与 geo*.dat (→ shareDir; 缺失不阻断).
func unzipXray(zipPath, binPath, shareDir string) error {
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer zr.Close()
	gotBin := false
	for _, f := range zr.File {
		name := filepath.Base(f.Name)
		switch {
		case name == "xray":
			if err := extractZipFile(f, binPath); err != nil {
				return err
			}
			gotBin = true
		case strings.HasPrefix(name, "geo") && strings.HasSuffix(name, ".dat"):
			_ = extractZipFile(f, filepath.Join(shareDir, name)) // geo 缺失不阻断
		}
	}
	if !gotBin {
		return fmt.Errorf("zip 内未找到 xray binary")
	}
	return nil
}

func extractZipFile(f *zip.File, dst string) error {
	rc, err := f.Open()
	if err != nil {
		return err
	}
	defer rc.Close()
	w, err := os.OpenFile(dst, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	defer w.Close()
	_, err = io.Copy(w, rc)
	return err
}
