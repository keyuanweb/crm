import { useTranslation } from 'react-i18next'
import { FileProtectOutlined, ClockCircleOutlined, UsergroupAddOutlined, TrophyOutlined } from '@ant-design/icons'
import type { SuggestionSummary } from '../types'

interface SuggestionCardsProps {
  /** 智能建议摘要数据 */
  data: SuggestionSummary
  /** 自定义样式 */
  style?: React.CSSProperties
}

/**
 * 智能建议摘要组件
 * 卡片式展示，带图标和颜色编码
 */
export default function SuggestionCards({ data, style }: SuggestionCardsProps) {
  const { t } = useTranslation()
  const suggestions = [
    {
      label: t('pages.dataVision.suggestions.atRisk'),
      value: data.atRiskCustomers,
      icon: <FileProtectOutlined />,
      color: '#ff4d4f',
      bgColor: 'rgba(255, 77, 79, 0.1)',
    },
    {
      label: t('pages.dataVision.suggestions.stalled'),
      value: data.stalledOpportunities,
      icon: <ClockCircleOutlined />,
      color: '#fa8c16',
      bgColor: 'rgba(250, 140, 22, 0.1)',
    },
    {
      label: t('pages.dataVision.suggestions.followUp'),
      value: data.followUpCustomers,
      icon: <UsergroupAddOutlined />,
      color: '#4da3ff',
      bgColor: 'rgba(77, 163, 255, 0.1)',
    },
    {
      label: t('pages.dataVision.suggestions.highScoreLeads'),
      value: data.highScoreLeads,
      icon: <TrophyOutlined />,
      color: '#52c41a',
      bgColor: 'rgba(82, 196, 26, 0.1)',
    },
  ]

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, ...style }}>
      {suggestions.map((item) => (
        <div
          key={item.label}
          style={{
            background: item.bgColor,
            border: `1px solid ${item.color}30`,
            borderRadius: 8,
            padding: '12px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: item.bgColor,
              border: `1px solid ${item.color}40`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: item.color,
              fontSize: 18,
            }}
          >
            {item.icon}
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#7db4ff', marginBottom: 2 }}>{item.label}</div>
            <div style={{ fontSize: 20, fontWeight: 700, color: item.color }}>{item.value}</div>
          </div>
        </div>
      ))}
    </div>
  )
}
