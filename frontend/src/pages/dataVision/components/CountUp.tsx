import { useCountUp } from '../hooks/useCountUp'

interface CountUpProps {
  /** 目标数值 */
  value: number
  /** 动画持续时间（毫秒） */
  duration?: number
  /** 小数位数 */
  decimals?: number
  /** 数字格式化函数 */
  formatter?: (value: number) => string
  /** 自定义样式 */
  style?: React.CSSProperties
  /** 自定义类名 */
  className?: string
}

/**
 * 数字滚动动画组件
 */
export default function CountUp({
  value,
  duration = 1000,
  decimals = 0,
  formatter,
  style,
  className,
}: CountUpProps) {
  const animatedValue = useCountUp(value, duration, decimals)
  const displayValue = formatter ? formatter(animatedValue) : animatedValue.toFixed(decimals)

  return (
    <span className={className} style={style}>
      {displayValue}
    </span>
  )
}
