import request from '@/api/request'
import { useUserStore } from '@/stores/user'

/** 探活结果 (后端 ConnectivityTestRespVO). */
export interface ConnectivityTestResult {
  success: boolean
  elapsedMs: number
  error?: string
}

/** 操作系统级别基本信息 (后端 ServerSystemInfoRespVO); 不依赖 Xray 是否在跑. */
export interface ServerSystemInfo {
  hostname?: string
  kernel?: string
  osRelease?: string
  systemUptime?: string
  loadAvg?: string
  memory?: string
  disk?: string
  timezone?: string
}

/** Xray systemd 服务运行状态 (后端 ServiceStatusRespVO); 不含日志. */
export interface XrayServiceStatus {
  /** systemd unit 名, xray-managed 接口固定为 "xray" */
  unit?: string
  active?: string
  version?: string
  uptimeFrom?: string
  /** 监听端口列表 (ss -ltn 抓取相关行); 多行字符串, 前端按 \n 拆分展示 */
  listening?: string
  /** systemctl is-enabled 输出: enabled / disabled / static / masked / ... */
  enabled?: string
}

/** 通用 systemd 状态 (后端 SystemdStatusRespVO); 不含 service 专属字段. */
export interface SystemdStatus {
  unit?: string
  active?: string
  uptimeFrom?: string
  enabled?: string
}

/** 日志级别过滤 (journalctl -p 语义). */
export type XrayLogLevel = 'all' | 'warning' | 'err'

/** systemd unit 日志快照 (后端 ServiceLogRespVO). */
export interface XrayLog {
  unit?: string
  lines: number
  level: XrayLogLevel
  log?: string
}

// ===== 后端 ServerInspectorController @ /admin/node/server (通用, 不绑 xray) =====

/** 探活: SSH 跑 'true' 验证可达性; 失败已包成 success=false 不抛错. */
export function testServerConnectivity(serverId: string) {
  return request.post<unknown, ConnectivityTestResult>(`/admin/node/server/${serverId}/test`)
}

/** 拉服务器系统基本信息 (hostname / 内存 / 磁盘 / 时区 等). */
export function getServerSystemInfo(serverId: string) {
  return request.get<unknown, ServerSystemInfo>(`/admin/node/server/${serverId}/system-info`)
}

/** 拉指定 systemd unit 的通用运行状态 (不含 service 专属字段如 version/listening). */
export function getSystemdStatus(serverId: string, unit: string) {
  return request.get<unknown, SystemdStatus>(
    `/admin/node/server/${serverId}/systemd-status`,
    { params: { unit } }
  )
}

/** 拉指定 systemd unit 的 journalctl 日志, 按行数 + 级别过滤. */
export function getServiceLog(
  serverId: string,
  unit: string,
  opts?: { lines?: number; level?: XrayLogLevel }
) {
  return request.get<unknown, XrayLog>(`/admin/node/server/${serverId}/log`, {
    params: {
      unit,
      lines: opts?.lines,
      level: opts?.level === 'all' ? undefined : opts?.level
    }
  })
}

/** 拉 Xray 的 journalctl 日志; 是 getServiceLog 的 unit=xray 便捷封装. */
export function getXrayLog(
  serverId: string,
  opts?: { lines?: number; level?: XrayLogLevel }
) {
  return getServiceLog(serverId, 'xray', opts)
}

// ===== 后端 XrayServerManageController @ /admin/node/xray/server =====

/** 拉 Xray 服务运行状态 (active / version / 启动时间 / 监听端口 / 开机自启). */
export function getXrayServiceStatus(serverId: string) {
  return request.get<unknown, XrayServiceStatus>(`/admin/node/xray/server/${serverId}/status`)
}

/** 重启 Xray 服务; 客户连接会断 1-2 秒. */
export function xrayRestart(serverId: string) {
  return request.post<unknown, string>(`/admin/node/xray/server/${serverId}/restart`)
}

/** 开/关 Xray 开机自启 (systemctl enable/disable); 末尾返回 is-enabled 结果给前端确认. */
export function xrayAutostart(serverId: string, enabled: boolean) {
  return request.post<unknown, string>(
    `/admin/node/xray/server/${serverId}/autostart`,
    null,
    { params: { enabled } }
  )
}

/**
 * Xray 线路服务器一键安装入参.
 *
 * 阶段 1 改造 (1:1 + slot 模型):
 *   - 删除 vmessPort (旧共享 inbound 模型不存在了)
 *   - 加 xrayVersion / slotPortBase / slotPoolSize (1:1 模型核心参数)
 *   - 加 installSwap / swapSizeMb (模块化勾选, 小内存机推荐)
 */
export interface LineServerInstallDTO {
  /** Xray 版本; "v1.8.23" 这种或 "latest". 默认从后端常量取. */
  xrayVersion?: string
  /** 客户端口段起点; 1:1 模型每客户独享 inbound 监听 base+slotIndex. 默认 30000. */
  slotPortBase?: number
  /** Slot 池大小, server 最多承载客户数. 默认 50. */
  slotPoolSize?: number
  /** Xray gRPC API 端口 (loopback, 走 SSH 隧道). */
  xrayApiPort: number
  /** xray 日志目录, 留空 = /var/log/xray. */
  logDir?: string
  installUfw?: boolean
  enableBbr?: boolean
  /** 是否启用 swap (小内存 VPS 推荐). */
  installSwap?: boolean
  /** swap 大小 MB, installSwap=true 时生效. 默认 1024. */
  swapSizeMb?: number
  /** IANA tz, 如 Asia/Shanghai / UTC; 留空或 "skip" 不改远端时区. */
  timezone?: string
}

/**
 * 一键安装/重装 Xray — 流式接口.
 * 后端用 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调 onChunk.
 *
 * - axios 不支持 response 流式, 这里直接用 fetch + 手写认证头.
 * - signal 支持外部 abort (用户中途关弹框时取消请求).
 * - 整体超时由 server 端 ResponseBodyEmitter 设了 15 分钟; 前端不再额外限时.
 */
export async function xrayInstallStream(
  serverId: string,
  dto: LineServerInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(`/api/admin/node/xray/server/${serverId}/install`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: userStore.token
    },
    body: JSON.stringify(dto),
    signal
  })
  if (res.status === 401) {
    userStore.clear()
    throw new Error('登录已过期, 请重新登录')
  }
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应 (Response.body 为空)')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const text = decoder.decode(value, { stream: true })
    if (text) onChunk(text)
  }
  // 收尾解码空状态(避免 multibyte 字符断在 chunk 边界丢字)
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}
