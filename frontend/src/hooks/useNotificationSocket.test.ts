import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useNotificationSocket } from './useNotificationSocket'

/** mock 全局 WebSocket。 */
class MockWebSocket {
  static instances: MockWebSocket[] = []
  readyState = 0 // CONNECTING
  onopen: (() => void) | null = null
  onmessage: ((ev: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  url: string
  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }
  close() {
    this.readyState = 3
    this.onclose?.()
  }
  static OPEN = 1
  static CONNECTING = 0
}

describe('useNotificationSocket（026 实时通知 hook）', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    localStorage.clear()
    // @ts-expect-error mock 全局
    globalThis.WebSocket = MockWebSocket
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('建立连接并携带 token', () => {
    localStorage.setItem('accessToken', 'test-token')
    const onMsg = vi.fn()
    renderHook(() => useNotificationSocket(onMsg, vi.fn()))

    expect(MockWebSocket.instances).toHaveLength(1)
    expect(MockWebSocket.instances[0].url).toContain('/ws/notifications?token=test-token')
  })

  it('收到推送消息时回调（更新未读数）', () => {
    localStorage.setItem('accessToken', 'test-token')
    const onMsg = vi.fn()
    renderHook(() => useNotificationSocket(onMsg, vi.fn()))

    const ws = MockWebSocket.instances[0]
    ws.readyState = 1
    act(() => {
      ws.onmessage?.({ data: JSON.stringify({ id: 1, type: 'TICKET_ASSIGN', message: '新工单', unreadCount: 3 }) })
    })

    expect(onMsg).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, unreadCount: 3 }),
    )
  })

  it('断线后重连（指数退避）', () => {
    localStorage.setItem('accessToken', 'test-token')
    renderHook(() => useNotificationSocket(vi.fn(), vi.fn()))
    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.close() // 触发 onclose
    })
    // 首次退避 1s
    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(MockWebSocket.instances.length).toBeGreaterThanOrEqual(2)
  })

  it('连接 5 秒未成功时降级轮询（onFallback 调用）', () => {
    localStorage.setItem('accessToken', 'test-token')
    const onFallback = vi.fn()
    renderHook(() => useNotificationSocket(vi.fn(), onFallback))

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(onFallback).toHaveBeenCalled()
  })
})
