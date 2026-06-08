import request from '@/api/request'
import { streamPost } from '@/api/stream'

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

/**
 * 任意 systemd unit 的通用运行状态 (后端 SystemdStatusRespVO); 走公共 /admin/resource/server/get-systemd-status?unit=xxx
 * xray / dante 的 version 各自从 detail (DO) 拿, 不再放这里.
 */
export interface SystemdStatus {
  /** systemd unit 名, 跟传入 unit 一致 */
  unit?: string
  /** active / inactive / failed / unknown */
  active?: string
  /** ActiveEnterTimestamp 重格式化后的字符串 */
  uptimeFrom?: string
  /** systemctl is-enabled 输出: enabled / disabled / static / masked / ... */
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

/** 启用 swap 入参. */
export interface EnableSwapDTO {
  /** swap 大小 MB; 256-8192. */
  sizeMb: number
}

// ===== 后端 ResourceServerInfoController @ /admin/resource/server (通用, 不绑 xray) =====

/** 拉服务器系统基本信息 (hostname / 内存 / 磁盘 / 时区 等). */
export function getServerSystemInfo(serverId: string) {
  return request.get<unknown, ServerSystemInfo>('/admin/resource/server/get-system-info', { params: { id: serverId } })
}

/** 拉 UFW 防火墙状态 (ufw status verbose 原文); 未装 ufw 时回提示文案. */
export function getServerUfwStatus(serverId: string) {
  return request.get<unknown, string>('/admin/resource/server/get-ufw-status', { params: { id: serverId } })
}

/** 拉任意 systemd unit 的运行状态 (xray / danted 等通用). */
export function getServerSystemdStatus(serverId: string, unit: string) {
  return request.get<unknown, SystemdStatus>('/admin/resource/server/get-systemd-status', {
    params: { id: serverId, unit }
  })
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
  return request.get<unknown, XrayLog>('/admin/resource/server/get-service-log', {
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

// ===== 后端 ResourceServerOpsController @ /admin/resource/server (运维流式) =====

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
