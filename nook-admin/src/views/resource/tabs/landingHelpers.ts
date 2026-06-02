/**
 * 落地机详情 tab 共用辅助函数 / 类型. 抽出来避免每个 tab 重复定义.
 */
import { formatDateTime } from '@/utils/date'

export function statusTagType(status?: string): 'default' | 'success' | 'warning' | 'info' {
  switch (status) {
    case 'OCCUPIED': return 'warning'
    case 'AVAILABLE': return 'success'
    default: return 'default'
  }
}

export function relativeTime(iso?: string): string {
  if (!iso) return '-'
  const diffMs = Date.now() - new Date(iso).getTime()
  if (diffMs < 0) return formatDateTime(iso)
  const sec = Math.floor(diffMs / 1000)
  if (sec < 30) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return `${Math.floor(sec / 86400)} 天前`
}

export function maskSecret(s?: string): string {
  if (!s) return '-'
  if (s.length <= 12) return s.slice(0, 2) + '****' + s.slice(-2)
  return s.slice(0, 6) + '****' + s.slice(-4)
}

export function formatBytes(bytes?: number | null): string {
  if (bytes == null || bytes === 0) return '0 B'
  const gb = bytes / 1024 / 1024 / 1024
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${(bytes / 1024).toFixed(0)} KB`
}
