import { useEffect, useRef } from 'react'

/** WebSocket 推送消息（与后端 NotificationPushPayload 对齐）。 */
export interface NotificationPushPayload {
  id: number
  type: string
  message: string
  unreadCount: number
}

const WS_PROTOCOL = window.location.protocol === 'https:' ? 'wss:' : 'ws:'

/**
 * 通知 WebSocket hook（026-realtime-notify，FR-004/005/006）：
 * 连接监听实时推送；断线指数退避重连（1s→30s）；连接失败时回调 onFallback 供调用方降级轮询。
 */
export function useNotificationSocket(
  onMessage: (payload: NotificationPushPayload) => void,
  onFallback: () => void,
) {
  const onMessageRef = useRef(onMessage)
  const onFallbackRef = useRef(onFallback)
  const wsRef = useRef<WebSocket | null>(null)
  const retryRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const fallbackRef = useRef(false)
  const disposedRef = useRef(false)

  onMessageRef.current = onMessage
  onFallbackRef.current = onFallback

  useEffect(() => {
    disposedRef.current = false

    const connect = () => {
      if (disposedRef.current) return
      const token = localStorage.getItem('accessToken')
      if (!token) return
      try {
        const ws = new WebSocket(`${WS_PROTOCOL}//${window.location.host}/ws/notifications?token=${token}`)
        wsRef.current = ws

        ws.onopen = () => {
          retryRef.current = 0
        }

        ws.onmessage = (ev) => {
          try {
            const payload = JSON.parse(ev.data as string) as NotificationPushPayload
            onMessageRef.current(payload)
          } catch {
            // 忽略无法解析的消息
          }
        }

        ws.onclose = () => {
          if (disposedRef.current) return
          // 断线重连（指数退避）
          const delay = Math.min(30000, 1000 * 2 ** retryRef.current)
          retryRef.current++
          timerRef.current = setTimeout(connect, delay)
        }

        ws.onerror = () => {
          // onerror 后必触发 onclose，重连由 onclose 处理；首次连接失败也走重连
        }
      } catch {
        // WebSocket 构造失败（如不支持）→ 降级轮询
        fallbackRef.current = true
        onFallbackRef.current()
      }
    }

    connect()

    // 若 5 秒内未成功建立连接（可能后端不支持 WS），降级轮询
    const fallbackTimer = setTimeout(() => {
      if (!fallbackRef.current && (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN)) {
        fallbackRef.current = true
        onFallbackRef.current()
      }
    }, 5000)

    return () => {
      disposedRef.current = true
      if (timerRef.current) clearTimeout(timerRef.current)
      clearTimeout(fallbackTimer)
      if (wsRef.current) wsRef.current.close()
      wsRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return null
}
