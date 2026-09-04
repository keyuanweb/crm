import type { ReactNode } from 'react'

export interface AmountDisplayProps {
  /** 金额数值 */
  value: number | string
  /** 货币符号 */
  currency?: string
  /** 是否显示小数 */
  showDecimals?: boolean
  /** 自定义类名 */
  className?: string
  /** 额外内容（如趋势指示） */
  extra?: ReactNode
  /** 是否右对齐 */
  alignRight?: boolean
}

/**
 * 金额显示组件
 * 货币格式化，右对齐，支持趋势指示
 */
export default function AmountDisplay({
  value,
  currency = '¥',
  showDecimals = true,
  className = '',
  extra,
  alignRight = true,
}: AmountDisplayProps) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  
  if (isNaN(numValue)) {
    return <span className={className}>-</span>
  }

  const formatted = showDecimals
    ? numValue.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    : numValue.toLocaleString('zh-CN', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
      })

  return (
    <span
      className={className}
      style={{
        fontFamily: 'Inter, PingFang SC, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontWeight: 500,
        textAlign: alignRight ? 'right' : 'left',
        display: 'inline-flex',
        alignItems: 'center',
        gap: extra ? 8 : 0,
        whiteSpace: 'nowrap',
      }}
    >
      <span>
        {currency}
        {formatted}
      </span>
      {extra}
    </span>
  )
}
