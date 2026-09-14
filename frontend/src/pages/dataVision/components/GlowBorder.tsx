interface GlowBorderProps {
  children: React.ReactNode
  /** 边框颜色 */
  color?: string
  /** 发光强度 */
  intensity?: number
  /** 自定义样式 */
  style?: React.CSSProperties
  /** 自定义类名 */
  className?: string
}

/**
 * 流光边框组件
 * 使用 CSS Animation 实现流光效果
 */
export default function GlowBorder({
  children,
  color = '#4da3ff',
  intensity = 0.3,
  style,
  className,
}: GlowBorderProps) {
  return (
    <div
      className={className}
      style={{
        position: 'relative',
        background: 'rgba(13, 32, 66, 0.85)',
        borderRadius: 'var(--radius-lg)',
        padding: 2,
        ...style,
      }}
    >
      {/* 流光边框 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          borderRadius: 'var(--radius-lg)',
          background: `linear-gradient(90deg, ${color}, ${color}80, ${color})`,
          backgroundSize: '200% 100%',
          animation: 'glowBorder 3s linear infinite',
          zIndex: -1,
        }}
      />
      {/* 发光效果 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          borderRadius: 'var(--radius-lg)',
          boxShadow: `0 0 ${intensity * 20}px ${intensity * 10}px ${color}40`,
          zIndex: -1,
        }}
      />
      {/* 内容 */}
      <div
        style={{
          height: '100%',
          width: '100%',
          borderRadius: 'var(--radius-lg)',
          background: 'rgba(13, 32, 66, 0.95)',
          padding: '16px 18px',
          overflow: 'hidden',
        }}
      >
        {children}
      </div>
    </div>
  )
}
