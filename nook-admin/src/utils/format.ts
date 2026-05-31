/** 字节数转可读字符串 (二进制进位, 与后端 GB=1024^3 口径一致). */
export function formatBytes(bytes?: number | null, fractionDigits = 2): string {
  if (bytes == null || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(i === 0 ? 0 : fractionDigits)} ${units[i]}`
}

/** 大数紧凑展示: <1万原样, ≥1万显示 x.x万 (落地机/订阅等计数用, 避免长数字撑破布局). */
export function compactCount(n?: number | null): string {
  const v = n ?? 0
  if (v < 10000) return String(v)
  const w = v / 10000
  return (Number.isInteger(w) ? w.toFixed(0) : w.toFixed(1)) + '万'
}

const BYTES_PER_GB = 1024 ** 3

/** 已用字节占总配额(GB)的百分比, 上限 100; 总配额为 0/空视为不限返 0. */
export function trafficPercent(usedBytes?: number | null, totalGb?: number | null): number {
  const total = (totalGb ?? 0) * BYTES_PER_GB
  if (total <= 0) return 0
  return Math.min(100, Math.round(((usedBytes ?? 0) / total) * 100))
}
