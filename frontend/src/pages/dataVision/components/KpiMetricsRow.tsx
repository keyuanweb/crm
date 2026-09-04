import type { KpiMetrics } from '../types'
import KpiMetricCard from './KpiMetricCard'

interface KpiMetricsRowProps {
  /** KPI 指标数据 */
  kpi: KpiMetrics
  /** 自定义样式 */
  style?: React.CSSProperties
}

/**
 * KPI 指标卡行组件
 * 展示 6 个核心 KPI 指标
 */
export default function KpiMetricsRow({ kpi, style }: KpiMetricsRowProps) {
  return (
    <div
      className="kpi-metrics-row"
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: 20,
        padding: '20px 30px',
        position: 'relative',
        zIndex: 10,
        ...style,
      }}
    >
      <KpiMetricCard
        label="商机总数"
        value={kpi.opportunityCount}
        color="#4da3ff"
      />
      <KpiMetricCard
        label="金额合计"
        value={kpi.amountTotal}
        unit="元"
        color="#7dd3fc"
        decimals={2}
      />
      <KpiMetricCard
        label="赢单率"
        value={kpi.winRate * 100}
        unit="%"
        color="#52c41a"
        decimals={1}
      />
      <KpiMetricCard
        label="客户总数"
        value={kpi.customerCount}
        color="#fa8c16"
      />
      <KpiMetricCard
        label="活跃客户"
        value={kpi.activeCustomerCount}
        color="#b37feb"
      />
      <KpiMetricCard
        label="本月新增客户"
        value={kpi.newCustomersThisMonth}
        color="#ff85c0"
      />
    </div>
  )
}
