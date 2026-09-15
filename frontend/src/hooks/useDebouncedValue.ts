import { useEffect, useState } from 'react'

/**
 * 值防抖：返回「停止变化 `delay` 毫秒之后」的那个值（095 T003，全仓首个防抖件）。
 *
 * ## 三条必须知道的行为
 *
 * - **首帧不延迟**：初始值由 `useState(value)` 直接给出，所以挂载时立刻是当前值，
 *   不会先渲染一帧「空值再补上」。
 * - **窗口内返回的是旧值**：连续输入时，返回的始终是上一次提交的值，
 *   直到停止输入满 `delay`。
 * - **卸载时清定时器**：`useEffect` 的清理函数负责，避免卸载后 `setState`。
 *
 * ## 为什么是一个 hook 而不是就地在页面里 `setTimeout`
 *
 * 就地写会把同一段时序逻辑塞进页面，且**无法单测**——页面级的 fake timers 会与
 * antd 内部的定时器、以及 testing-library 的 `findBy*` 等待互相缠死。
 * 抽出来之后，「窗口内没有提交新值」这条**只在 hook 自己的单测里量**；
 * 页面层只量「最终结果与不防抖时一致」，这一点在 `quickstart.md` 里如实写明。
 *
 * @param value 原始值
 * @param delay 毫秒，默认 300（该阈值来自 `068` 的 T015，**不自行发明**）
 */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
