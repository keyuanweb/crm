import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        label={t('pages.dataVision.kpi.opportunityCount')}
        value={kpi.opportunityCount}
        color="#4da3ff"
      />
      <KpiMetricCard
        label={t('pages.dataVision.kpi.amountTotal')}
        value={kpi.amountTotal}
        unit={t('pages.dataVision.kpi.unitYuan')}
        color="#7dd3fc"
        decimals={2}
      />
      <KpiMetricCard
        label={t('pages.dataVision.kpi.winRate')}
        value={kpi.winRate * 100}
        unit="%"
        color="#52c41a"
        decimals={1}
      />
      <KpiMetricCard
        label={t('pages.dataVision.kpi.customerCount')}
        value={kpi.customerCount}
        color="#fa8c16"
      />
      <KpiMetricCard
        label={t('pages.dataVision.kpi.activeCustomerCount')}
        value={kpi.activeCustomerCount}
        color="#b37feb"
      />
      <KpiMetricCard
        label={t('pages.dataVision.kpi.newCustomersThisMonth')}
        value={kpi.newCustomersThisMonth}
        color="#ff85c0"
      />
    </div>
  )
}
