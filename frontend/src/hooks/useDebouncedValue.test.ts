import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDebouncedValue } from './useDebouncedValue'

describe('useDebouncedValue（095 T003：值防抖）', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('首帧不延迟：挂载时立刻返回当前值', () => {
    const { result } = renderHook(() => useDebouncedValue('华东', 300))
    expect(result.current).toBe('华东')
  })

  it('窗口内返回旧值，满 300ms 后才提交新值', () => {
    const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 300), {
      initialProps: { v: '华' },
    })
    expect(result.current).toBe('华')

    rerender({ v: '华东' })
    // 未到窗口：仍是旧值 —— 这一条正是「不逐字符重算」的判据
    expect(result.current).toBe('华')

    act(() => {
      vi.advanceTimersByTime(299)
    })
    expect(result.current).toBe('华')

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(result.current).toBe('华东')
  })

  it('连续快速变化只提交最后一个值（中间值被丢弃）', () => {
    const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 300), {
      initialProps: { v: '' },
    })

    for (const v of ['华', '华东', '华东销', '华东销售', '华东销售部']) {
      rerender({ v })
      act(() => {
        vi.advanceTimersByTime(100) // 每次都没到 300ms
      })
    }

    // 五轮共推进 500ms，但每轮都被下一次变化重置 ⇒ 仍停在初始值
    expect(result.current).toBe('')

    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(result.current).toBe('华东销售部')
  })

  it('delay 可覆盖（自定义 50ms）', () => {
    const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 50), {
      initialProps: { v: 'a' },
    })

    rerender({ v: 'b' })
    expect(result.current).toBe('a')

    act(() => {
      vi.advanceTimersByTime(50)
    })
    expect(result.current).toBe('b')
  })

  it('卸载时清掉定时器（不会在卸载后 setState）', () => {
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout')
    const { rerender, unmount } = renderHook(({ v }) => useDebouncedValue(v, 300), {
      initialProps: { v: 'a' },
    })

    rerender({ v: 'b' }) // 挂上一个未到期的定时器
    const callsBeforeUnmount = clearSpy.mock.calls.length

    unmount()
    expect(clearSpy.mock.calls.length).toBeGreaterThan(callsBeforeUnmount)

    // 卸载后推进计时：若清理失效，这里会触发对已卸载组件的 setState
    expect(() =>
      act(() => {
        vi.advanceTimersByTime(1000)
      }),
    ).not.toThrow()

    clearSpy.mockRestore()
  })
})
