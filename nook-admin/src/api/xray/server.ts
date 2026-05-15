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
  /** 关键词回显; 空表示本次未过滤 */
  keyword?: string
  log?: string
}

// ===== 后端 ResourceServerInfoController @ /admin/resource/server (通用, 不绑 xray) =====

/** 探活: SSH 跑 'true' 验证可达性; 失败已包成 success=false 不抛错. */
export function testServerConnectivity(serverId: string) {
  return request.post<unknown, ConnectivityTestResult>('/admin/resource/server/connectivity-test', null, { params: { id: serverId } })
}

/** 拉服务器系统基本信息 (hostname / 内存 / 磁盘 / 时区 等). */
export function getServerSystemInfo(serverId: string) {
  return request.get<unknown, ServerSystemInfo>('/admin/resource/server/system-info', { params: { id: serverId } })
}

/** 拉指定 systemd unit 的通用运行状态 (不含 service 专属字段如 version/listening). */
export function getSystemdStatus(serverId: string, unit: string) {
  return request.get<unknown, SystemdStatus>('/admin/resource/server/systemd-status',
    { params: { id: serverId, unit } })
}

/**
 * 拉指定 systemd unit 的 journalctl 日志, 按行数 + 级别 + 关键词过滤.
 * keyword: 子串匹配, 大小写不敏感; 后端走 grep -F -i, 空 / undefined 不过滤.
 * 注意 lines 是 journalctl 拉的原始末尾行数, keyword 在这些行里再做子串过滤.
 */
export function getServiceLog(
  serverId: string,
  unit: string,
  opts?: { lines?: number; level?: XrayLogLevel; keyword?: string }
) {
  return request.get<unknown, XrayLog>('/admin/resource/server/service-log', {
    params: {
      id: serverId,
      unit,
      lines: opts?.lines,
      level: opts?.level === 'all' ? undefined : opts?.level,
      keyword: opts?.keyword?.trim() || undefined
    }
  })
}

/** 拉 Xray 的 journalctl 日志; 是 getServiceLog 的 unit=xray 便捷封装. */
export function getXrayLog(
  serverId: string,
  opts?: { lines?: number; level?: XrayLogLevel; keyword?: string }
) {
  return getServiceLog(serverId, 'xray', opts)
}

// ===== 后端 XrayServerManageController @ /admin/xray/server =====

/** 拉 Xray 服务运行状态 (active / version / 启动时间 / 监听端口 / 开机自启). */
export function getXrayServiceStatus(serverId: string) {
  return request.get<unknown, XrayServiceStatus>('/admin/xray/server/status', { params: { id: serverId } })
}

/** 重启 Xray 服务; 客户连接会断 1-2 秒. */
export function xrayRestart(serverId: string) {
  return request.post<unknown, string>('/admin/xray/server/restart', null, { params: { id: serverId } })
}

/** 开/关 Xray 开机自启 (systemctl enable/disable); 末尾返回 is-enabled 结果给前端确认. */
export function xrayAutostart(serverId: string, enabled: boolean) {
  return request.post<unknown, string>('/admin/xray/server/autostart', null, { params: { id: serverId, enabled } })
}

/**
 * Xray 线路服务器一键安装入参 (1:1 + slot 模型).
 *
 * <p>仅 xray 内核 + slot 池 + UFW + 时区; swap / bbr 等通用 OS 调优拆到 ServerOps 接口,
 * 在服务器列表"运维操作"菜单按需独立触发, 不混进部署链路.
 *
 * <p>所有字段都必须由前端传入, 后端不再 fallback (LineServerInstallReqVOValidator 严校验).
 */
export interface LineServerInstallDTO {
  /** Xray 版本; "v26.3.27" 这种或 "latest". */
  xrayVersion: string
  /**
   * Xray 安装目录; binary / config / share 全装在 <installDir>/{bin,etc,share} 下.
   * 必须绝对路径; 后端校验黑名单 (/, /etc, /usr, /var, /bin, /sbin, /lib, /boot, /dev, /proc, /sys, /run, /root).
   */
  installDir: string
  /** 客户端口段起点; 1:1 模型每客户独享 inbound 监听 base+slotIndex. */
  slotPortBase: number
  /** Slot 池大小, server 最多承载客户数. */
  slotPoolSize: number
  /** Xray 内置 api server 端口 (loopback); xray api adi/rmi 用. */
  xrayApiPort: number
  /** xray 日志目录; 留空时后端派生为 <installDir>/logs. */
  logDir: string
  /** Xray 日志级别: debug / info / warning / error / none. */
  logLevel: 'debug' | 'info' | 'warning' | 'error' | 'none'
  /** systemd Restart= 策略: always / on-failure / no. */
  restartPolicy: 'always' | 'on-failure' | 'no'
  /** 是否 systemctl enable xray (机器重启后自动起 xray). */
  enableOnBoot: boolean
  /** 强制重装; 即使版本号一致也走下载流程, 用于自编译 / build 后缀差异. */
  forceReinstall: boolean
  installUfw: boolean
  /** IANA tz, 如 Asia/Shanghai / UTC; "skip" 表示不改远端时区. */
  timezone: string
}

/** 启用 swap 入参. */
export interface EnableSwapDTO {
  /** swap 大小 MB; 256-8192. */
  sizeMb: number
}

/**
 * 流式 POST 工具: 后端 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调 onChunk.
 *
 * - axios 不支持 response 流式, 走 fetch + 手写认证头.
 * - signal 支持外部 abort (用户中途关弹框时取消请求).
 * - 整体超时由 server 端 ResponseBodyEmitter 控制; 前端不再额外限时.
 */
async function streamPost(
  url: string,
  body: unknown | undefined,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: userStore.token
    },
    body: body === undefined ? undefined : JSON.stringify(body),
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

/** 一键安装/重装 Xray (流式). */
export function xrayInstallStream(
  serverId: string,
  dto: LineServerInstallDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  return streamPost(`/api/admin/xray/server/install?id=${encodeURIComponent(serverId)}`, dto, onChunk, signal)
}

// ===== 后端 ResourceServerOpsController @ /admin/resource/server =====

/** 启用 swap (流式 stdout); 已有 swap 跳过, 不影响业务. */
export function enableSwapStream(
  serverId: string,
  dto: EnableSwapDTO,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  return streamPost(`/api/admin/resource/server/enable-swap?id=${encodeURIComponent(serverId)}`, dto, onChunk, signal)
}

/** 启用 BBR (流式 stdout); 内核不支持时跳过, 不影响业务. */
export function enableBbrStream(
  serverId: string,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  return streamPost(`/api/admin/resource/server/enable-bbr?id=${encodeURIComponent(serverId)}`, undefined, onChunk, signal)
}
