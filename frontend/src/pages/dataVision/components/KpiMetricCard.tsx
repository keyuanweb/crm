import CountUp from './CountUp'
import GlowBorder from './GlowBorder'

interface KpiMetricCardProps {
  /** 指标标签 */
  label: string
  /** 指标数值 */
  value: number
  /** 数值单位 */
  unit?: string
  /** 颜色 */
  color: string
  /** 小数位数 */
  decimals?: number
  /** 自定义样式 */
  style?: React.CSSProperties
}

/**
 * KPI 指标卡组件
 * 带有数字滚动动画和流光边框
 */
export default function KpiMetricCard({
  label,
  value,
  unit = '',
  color,
  decimals = 0,
  style,
}: KpiMetricCardProps) {
  return (
    <GlowBorder color={color} intensity={0.3} style={style}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 13, color: '#7db4ff', marginBottom: 8, letterSpacing: 1 }}>
          {label}
        </div>
        <div style={{ fontSize: 28, fontWeight: 700, color: color, lineHeight: 1.2 }}>
          <CountUp value={value} duration={1500} decimals={decimals} />
          {unit && <span style={{ fontSize: 14, marginLeft: 4 }}>{unit}</span>}
        </div>
      </div>
    </GlowBorder>
  )
}
