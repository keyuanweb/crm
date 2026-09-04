import { useEffect, useRef, useState } from 'react'

/**
 * 数字滚动动画 Hook
 * @param targetValue 目标数值
 * @param duration 动画持续时间（毫秒），默认 1000
 * @param decimals 小数位数，默认 0
 * @returns 当前动画数值
 */
export function useCountUp(
  targetValue: number,
  duration: number = 1000,
  decimals: number = 0,
): number {
  const [value, setValue] = useState(targetValue)
  const startTimeRef = useRef<number | null>(null)
  const previousValueRef = useRef(targetValue)
  const animationFrameRef = useRef<number | null>(null)

  useEffect(() => {
    const startTime = performance.now()
    startTimeRef.current = startTime
    const previousValue = previousValueRef.current

    const animate = (currentTime: number) => {
      if (!startTimeRef.current) return

      const elapsed = currentTime - startTimeRef.current
      const progress = Math.min(elapsed / duration, 1)

      // 使用 easeOutExpo 缓动函数
      const eased = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress)
      const currentValue = previousValue + (targetValue - previousValue) * eased

      setValue(parseFloat(currentValue.toFixed(decimals)))

      if (progress < 1) {
        animationFrameRef.current = requestAnimationFrame(animate)
      } else {
        setValue(targetValue)
        previousValueRef.current = targetValue
      }
    }

    animationFrameRef.current = requestAnimationFrame(animate)

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current)
      }
    }
  }, [targetValue, duration, decimals])

  return value
}
