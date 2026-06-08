import { useUserStore } from '@/stores/user'

/**
 * 流式 POST 工具: 后端 chunked transfer 边跑边吐 stdout, 前端 fetch + ReadableStream 边读边回调 onChunk.
 *
 * - axios 不支持 response 流式, 走 fetch + 手写认证头.
 * - signal 支持外部 abort (用户中途关弹框时取消请求).
 * - 整体超时由 server 端 ResponseBodyEmitter 控制; 前端不再额外限时.
 */
export async function streamPost(
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
