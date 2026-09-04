interface NeonTextProps {
  children: React.ReactNode
  /** 文字颜色 */
  color?: string
  /** 发光强度 */
  intensity?: number
  /** 自定义样式 */
  style?: React.CSSProperties
  /** 自定义类名 */
  className?: string
}

/**
 * 霓虹文字组件
 * 使用 text-shadow 实现霓虹发光效果
 */
export default function NeonText({
  children,
  color = '#4da3ff',
  intensity = 1,
  style,
  className,
}: NeonTextProps) {
  return (
    <span
      className={className}
      style={{
        color: color,
        textShadow: `0 0 ${intensity * 5}px ${color}80, 0 0 ${intensity * 10}px ${color}40, 0 0 ${intensity * 15}px ${color}20`,
        fontWeight: 700,
        letterSpacing: 2,
        ...style,
      }}
    >
      {children}
    </span>
  )
}
