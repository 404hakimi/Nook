package xraydeploy

import (
	"fmt"
	"io"
	"os"
	"strings"
	"time"
)

// writeConfig 写 config.json: 内置 api inbound + 后台下发的共享 inbound(不透明) + blackhole/api 静态出站.
// 业务出站/路由/用户全靠 reconcile 动态 adu/ado/adrules, 不落 config.json.
func writeConfig(out io.Writer, p paths, apiPort int, inboundJSON string) error {
	inboundJSON = strings.TrimSpace(inboundJSON)
	if inboundJSON == "" {
		return fmt.Errorf("inboundConfigJson 为空, 拼不出 config.json")
	}
	if old, err := os.ReadFile(p.config); err == nil {
		bak := fmt.Sprintf("%s.bak.%d", p.config, time.Now().Unix())
		_ = os.WriteFile(bak, old, 0o644)
		logf(out, "  原 config 备份到 %s", bak)
	}
	cfg := fmt.Sprintf(configTmpl, p.logDir, logLevel, apiPort, inboundJSON)
	if err := os.WriteFile(p.config, []byte(cfg), 0o644); err != nil {
		return fmt.Errorf("写 config.json 失败: %w", err)
	}
	logf(out, "✔ config.json 已写 (%s)", p.config)
	return nil
}

// writeSystemdUnit 写 xray.service; GOMEMLIMIT 按本机内存 70% 自适应 (防小机 OOM).
func writeSystemdUnit(out io.Writer, p paths) error {
	mib := goMemLimitMiB()
	unit := fmt.Sprintf(unitTmpl, p.bin, p.config, restartPolicy, p.share, mib)
	if err := os.WriteFile(p.unit, []byte(unit), 0o644); err != nil {
		return fmt.Errorf("写 systemd unit 失败: %w", err)
	}
	logf(out, "✔ systemd unit 已写 (%s); GOMEMLIMIT=%dMiB", p.unit, mib)
	return nil
}

// goMemLimitMiB 本机内存 70%, 下限 128MiB; 读不到 /proc/meminfo 回退 128.
func goMemLimitMiB() int {
	data, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return 128
	}
	for _, line := range strings.Split(string(data), "\n") {
		if strings.HasPrefix(line, "MemTotal:") {
			fields := strings.Fields(line)
			if len(fields) >= 2 {
				var kb int
				fmt.Sscanf(fields[1], "%d", &kb)
				if mib := kb * 70 / 100 / 1024; mib >= 128 {
					return mib
				}
			}
		}
	}
	return 128
}

const configTmpl = `{
  "log": { "access": "%[1]s/access.log", "error": "%[1]s/error.log", "loglevel": "%[2]s" },
  "api": { "tag": "api", "services": ["HandlerService", "LoggerService", "StatsService", "RoutingService"] },
  "stats": {},
  "policy": {
    "system": { "statsInboundUplink": true, "statsInboundDownlink": true, "statsOutboundUplink": true, "statsOutboundDownlink": true },
    "levels": { "0": { "statsUserUplink": true, "statsUserDownlink": true } }
  },
  "inbounds": [
    { "tag": "api", "listen": "127.0.0.1", "port": %[3]d, "protocol": "dokodemo-door", "settings": { "address": "127.0.0.1" } },
    %[4]s
  ],
  "outbounds": [
    { "tag": "blackhole", "protocol": "blackhole" },
    { "tag": "api", "protocol": "freedom" }
  ],
  "routing": { "domainStrategy": "AsIs", "rules": [ { "inboundTag": ["api"], "outboundTag": "api" } ] }
}
`

const unitTmpl = `[Unit]
Description=Xray Service (nook deployed)
Documentation=https://github.com/XTLS/Xray-core
After=network.target nss-lookup.target

[Service]
User=root
ExecStart=%[1]s run -c %[2]s
Restart=%[3]s
RestartSec=5
LimitNOFILE=65535
Environment=XRAY_LOCATION_ASSET=%[4]s
Environment=GOMEMLIMIT=%[5]dMiB

[Install]
WantedBy=multi-user.target
`
