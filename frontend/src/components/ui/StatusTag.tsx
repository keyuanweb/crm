import type { ReactNode } from 'react'

export type StatusTagType = 'success' | 'warning' | 'danger' | 'info' | 'default'

export interface StatusTagProps {
  /** 状态类型 */
  type?: StatusTagType
  /** 标签文本 */
  children: ReactNode
  /** 自定义类名 */
  className?: string
  /** 是否带边框 */
  bordered?: boolean
}

/**
 * 现代化状态标签组件
 * 带彩色圆点指示器，语义化颜色
 */
export default function StatusTag({
  type = 'default',
  children,
  className = '',
  bordered = false,
}: StatusTagProps) {
  return (
    <span
      className={`status-tag ${type} ${className}`}
      style={{
        ...(bordered
          ? { border: `1px solid var(--color-border)` }
          : {}),
      }}
    >
      {children}
    </span>
  )
}
