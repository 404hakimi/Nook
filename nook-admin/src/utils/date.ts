/**
 * 日期格式化。
 * 后端 fastjson2 默认序列化 LocalDateTime 为 "yyyy-MM-dd HH:mm:ss"，前端只截到分钟，
 * 列表场景去掉秒级噪音更干净；如确需秒，把 16 改成 19 即可。
 */

const EMPTY_PLACEHOLDER = '-'

/** "yyyy-MM-dd HH:mm:ss" → "yyyy-MM-dd HH:mm"。空/过短统一返回 "-"。 */
export function formatDateTime(s?: string | null): string {
  if (!s || s.length < 16) return EMPTY_PLACEHOLDER
  return s.slice(0, 16)
}

/** "yyyy-MM-dd HH:mm:ss" → "yyyy-MM-dd"。 */
export function formatDate(s?: string | null): string {
  if (!s || s.length < 10) return EMPTY_PLACEHOLDER
  return s.slice(0, 10)
}
