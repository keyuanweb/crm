import { useTranslation } from 'react-i18next'
import { Tag } from 'antd'
import type { LeaderboardItem } from '../types'

interface LeaderboardChartProps {
  /** 团队排行数据 */
  data: LeaderboardItem[]
  /** 自定义样式 */
  style?: React.CSSProperties
}

/**
 * 团队排行 Top10 组件
 * 带排名徽章和进度条
 */
export default function LeaderboardChart({ data, style }: LeaderboardChartProps) {
  const { t } = useTranslation()
  const top10 = data.slice(0, 10)

  return (
    <div style={{ overflowY: 'auto', height: '100%', ...style }}>
      {top10.length === 0 ? (
        <div style={{ textAlign: 'center', color: '#5a7cb8', padding: 20 }}>{t('pages.dataVision.leaderboard.empty')}</div>
      ) : (
        <div style={{ fontSize: 12 }}>
          {top10.map((item, index) => {
            let rankColor = '#7db4ff'
            let rankBg = 'rgba(77, 163, 255, 0.1)'
            
            if (index === 0) {
              rankColor = '#faad14'
              rankBg = 'rgba(250, 173, 20, 0.2)'
            } else if (index === 1) {
              rankColor = '#c0c0c0'
              rankBg = 'rgba(192, 192, 192, 0.2)'
            } else if (index === 2) {
              rankColor = '#cd7f32'
              rankBg = 'rgba(205, 127, 50, 0.2)'
            }

            return (
              <div
                key={item.userId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 0',
                  borderBottom: '1px solid rgba(255,255,255,0.05)',
                }}
              >
                <span
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: rankBg,
                    color: rankColor,
                    fontWeight: 700,
                    fontSize: 12,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  {index + 1}
                </span>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {item.displayName}
                </span>
                <span style={{ color: '#52c41a', fontWeight: 600, minWidth: 80, textAlign: 'right' }}>
                  {item.wonAmount.toLocaleString()} {t('pages.dataVision.leaderboard.unitYuan')}
                </span>
                <Tag
                  color={item.achievementRate == null ? 'default' : item.achievementRate >= 0.8 ? 'green' : item.achievementRate >= 0.5 ? 'gold' : 'red'}
                  style={{ margin: 0, fontSize: 11 }}
                >
                  {item.achievementRate == null ? t('pages.dataVision.leaderboard.noTarget') : `${Math.round(item.achievementRate * 100)}%`}
                </Tag>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
