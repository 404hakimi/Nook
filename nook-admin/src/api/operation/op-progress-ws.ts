import { useUserStore } from '@/stores/user'
import type { OpStatus, OpType } from './op-log'

/** 后端 OpProgressEvent 的 TS 镜像; 时间是毫秒. */
export interface OpProgressEvent {
  opId: string
  serverId?: string
  opType?: OpType
  status: OpStatus
  currentStep?: string
  progressPct?: number
  message?: string
  errorCode?: string
  errorMsg?: string
  timestamp: number
}

type Listener = (ev: OpProgressEvent) => void

/**
 * 单连接、多订阅复用; 切页面 / 取消订阅都走它, 避免每个组件各自起一条 socket.
 *
 * <p>状态机:
 * <pre>
 *   idle ──connect──▶ connecting ──open──▶ open ──close──▶ closed (1.5s 后重连)
 * </pre>
 *
 * <p>断线重连指数退避; subscribe 时若 socket 未就绪先入队, open 后批量 flush.
 */
class OpProgressClient {
  private socket: WebSocket | null = null
  private state: 'idle' | 'connecting' | 'open' | 'closed' = 'idle'
  // 订阅意向: opId → 监听器集合; 同一 opId 多个组件订阅可共享底层一次 server 推送
  private subscriptions = new Map<string, Set<Listener>>()
  // socket 未 open 时的待发指令; flush 在 open 后
  private pendingActions: Array<{ action: 'subscribe' | 'unsubscribe'; opId: string }> = []
  private reconnectAttempts = 0
  private reconnectTimer: number | null = null

  /** 订阅一个 opId; 返回取消订阅的函数. */
  subscribe(opId: string, listener: Listener): () => void {
    let set = this.subscriptions.get(opId)
    if (!set) {
      set = new Set()
      this.subscriptions.set(opId, set)
      this.send({ action: 'subscribe', opId })
    }
    set.add(listener)
    this.ensureConnected()
    return () => this.unsubscribe(opId, listener)
  }

  private unsubscribe(opId: string, listener: Listener) {
    const set = this.subscriptions.get(opId)
    if (!set) return
    set.delete(listener)
    if (set.size === 0) {
      this.subscriptions.delete(opId)
      this.send({ action: 'unsubscribe', opId })
    }
  }

  /** 关掉 socket; 用户登出 / app 卸载时调. */
  close() {
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.subscriptions.clear()
    this.pendingActions = []
    if (this.socket) {
      try { this.socket.close() } catch { /* noop */ }
    }
    this.socket = null
    this.state = 'idle'
  }

  private ensureConnected() {
    if (this.state === 'open' || this.state === 'connecting') return
    this.connect()
  }

  private connect() {
    const userStore = useUserStore()
    const token = userStore.token
    if (!token) return // 未登录不连

    // dev 走 vite proxy: ws://localhost:5173/ws/admin/operation/ws/op-progress
    // 生产同源由 nginx 代理 /ws/* → spring boot
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${scheme}://${window.location.host}/ws/admin/operation/ws/op-progress?token=${encodeURIComponent(token)}`

    this.state = 'connecting'
    let socket: WebSocket
    try {
      socket = new WebSocket(url)
    } catch (err) {
      console.warn('[op-ws] new WebSocket 失败', err)
      this.scheduleReconnect()
      return
    }
    this.socket = socket
    socket.onopen = () => {
      this.state = 'open'
      this.reconnectAttempts = 0
      // 重连后把活跃订阅重新发一次, 让后端 hub 重新建立 reverseIndex
      for (const opId of this.subscriptions.keys()) {
        this.rawSend({ action: 'subscribe', opId })
      }
      // pending 也 flush
      for (const a of this.pendingActions) this.rawSend(a)
      this.pendingActions = []
    }
    socket.onmessage = (e) => {
      try {
        const ev = JSON.parse(e.data) as OpProgressEvent
        const set = this.subscriptions.get(ev.opId)
        if (!set) return
        // 拷贝出来再迭代; 监听器内可能 unsubscribe 修改 set
        for (const l of Array.from(set)) {
          try { l(ev) } catch (err) { console.warn('[op-ws] listener 异常', err) }
        }
      } catch (err) {
        console.warn('[op-ws] 收到无效消息', e.data, err)
      }
    }
    socket.onclose = () => {
      this.state = 'closed'
      this.socket = null
      if (this.subscriptions.size > 0) this.scheduleReconnect()
    }
    socket.onerror = () => {
      // onclose 会跟着触发, 这里不重复
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return
    // 指数退避: 1s, 2s, 4s, 8s, 上限 30s
    const delay = Math.min(30_000, 1000 * 2 ** this.reconnectAttempts)
    this.reconnectAttempts++
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  private send(msg: { action: 'subscribe' | 'unsubscribe'; opId: string }) {
    if (this.state === 'open' && this.socket) {
      this.rawSend(msg)
    } else {
      this.pendingActions.push(msg)
      this.ensureConnected()
    }
  }

  private rawSend(msg: object) {
    try {
      this.socket?.send(JSON.stringify(msg))
    } catch (err) {
      console.warn('[op-ws] send 失败', err)
    }
  }
}

/** 全局单例; 跨组件复用同一条 WS 连接. */
export const opProgressClient = new OpProgressClient()
