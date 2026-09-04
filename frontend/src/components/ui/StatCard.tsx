import { CaretUpOutlined, CaretDownOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'

export interface StatCardProps {
  /** 数值 */
  value: string | number
  /** 标签/标题 */
  label: string
  /** 趋势变化（如 '+12%'） */
  trend?: string
  /** 趋势方向 */
  trendDirection?: 'up' | 'down'
  /** 图标 */
  icon?: ReactNode
  /** 额外内容 */
  extra?: ReactNode
  /** 自定义类名 */
  className?: string
}

/**
 * 现代化统计卡片组件
 * 参考 Linear/Vercel 设计风格
 */
export default function StatCard({
  value,
  label,
  trend,
  trendDirection,
  icon,
  extra,
  className = '',
}: StatCardProps) {
  return (
    <div className={`stat-card ${className}`}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="stat-card-value">{value}</div>
          <div className="stat-card-label">{label}</div>
        </div>
        {icon && (
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 'var(--radius-md)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20,
              background: 'var(--color-primary-light)',
              color: 'var(--color-primary)',
            }}
          >
            {icon}
          </div>
        )}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        {trend && trendDirection && (
          <span className={`stat-card-trend ${trendDirection}`}>
            {trendDirection === 'up' ? <CaretUpOutlined /> : <CaretDownOutlined />}
            {trend}
          </span>
        )}
        {extra && <div>{extra}</div>}
      </div>
    </div>
  )
}
