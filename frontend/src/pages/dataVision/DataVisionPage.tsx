import { useEffect, useRef, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Spin, Tag, Typography } from 'antd'
import { FullscreenExitOutlined, FullscreenOutlined, ReloadOutlined } from '@ant-design/icons'
import { fetchKpiBoard } from '../../services/kpiBoardService'
import type { KpiBoard } from '../../types/kpiBoard'
import ParticleBackground from './components/ParticleBackground'
import KpiMetricsRow from './components/KpiMetricsRow'
import FunnelChart from './components/FunnelChart'
import LeaderboardChart from './components/LeaderboardChart'
import HealthChart from './components/HealthChart'
import TrendChart from './components/TrendChart'
import SuggestionCards from './components/SuggestionCards'
import GlowBorder from './components/GlowBorder'
import NeonText from './components/NeonText'
import { useFullscreen } from './hooks/useFullscreen'
import { useResponsiveGrid } from './hooks/useResponsiveGrid'
import './styles/dataVision.css'

const { Text } = Typography

/** 面板容器 */
const Panel = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <GlowBorder color="#4da3ff" intensity={0.25}>
    <div style={{ color: '#7db4ff', fontSize: 14, fontWeight: 600, marginBottom: 12, letterSpacing: 1 }}>
      {title}
    </div>
    <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>{children}</div>
  </GlowBorder>
)

export default function DataVisionPage() {
  const { t } = useTranslation()
  const [data, setData] = useState<KpiBoard | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const refreshTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const { isFullscreen, toggleFullscreen } = useFullscreen()
  const responsiveConfig = useResponsiveGrid()

  const load = useCallback(async () => {
    try {
      const d = await fetchKpiBoard()
      setData(d)
      setError('')
      setLastUpdated(new Date())
      setLoading(false)
    } catch {
      setError(t('pages.dataVision.refreshFailed'))
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
    // 每 30 秒自动刷新
    refreshTimerRef.current = setInterval(() => void load(), 30000)
    return () => {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current)
    }
  }, [load])

  const handleManualRefresh = () => {
    setError('')
    void load()
  }

  const nowStr = new Date().toLocaleString('zh-CN', { hour12: false })

  // 计算列数
  const columns = responsiveConfig.columns

  return (
    <div className="data-vision-page">
      {/* 粒子动画背景 */}
      <ParticleBackground particleCount={100} maxLinkDistance={180} />

      {/* 顶部标题栏 */}
      <div className="data-vision-header">
        <div>
          <NeonText color="#4da3ff" intensity={1.5} style={{ fontSize: 28 }}>
            {t('pages.dataVision.title')}
          </NeonText>
          <Text style={{ color: '#5a7cb8', fontSize: 12, marginLeft: 16 }}>
            {t('pages.dataVision.autoRefreshHint')}{error && <span style={{ color: '#ff4d4f' }}> · {error}</span>}
          </Text>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Text style={{ color: '#7db4ff', fontSize: 14 }}>{nowStr}</Text>
          {lastUpdated && (
            <Tag color="blue" style={{ margin: 0, fontSize: 11 }}>
              {t('pages.dataVision.refreshedAt', { time: lastUpdated.toLocaleTimeString('zh-CN') })}
            </Tag>
          )}
          <Button
            size="small"
            icon={<ReloadOutlined spin={loading} />}
            onClick={handleManualRefresh}
            disabled={loading}
          >
            {t('pages.dataVision.refresh')}
          </Button>
          <Button
            size="small"
            icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            onClick={toggleFullscreen}
          >
            {isFullscreen ? t('pages.dataVision.exitFullscreen') : t('pages.dataVision.fullscreen')}
          </Button>
        </div>
      </div>

      {/* KPI 指标卡区域 */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40, zIndex: 10, position: 'relative' }}>
          <Spin size="large" />
        </div>
      ) : data ? (
        <>
          <KpiMetricsRow kpi={data.kpi} />

          {/* 图表区域 */}
          <div
            className="charts-grid"
            style={{
              '--columns': columns,
              gridTemplateColumns: `repeat(${columns}, 1fr)`,
              display: 'grid',
              gap: 20,
              padding: '0 30px 20px',
              position: 'relative',
              zIndex: 10,
            } as React.CSSProperties}
          >
            {/* 第一行：销售漏斗 + 团队排行 + 客户健康度 */}
            <Panel title={t('pages.dataVision.panel.salesFunnel')}>
              <FunnelChart stages={data.funnel.stages} />
            </Panel>

            <Panel title={t('pages.dataVision.panel.teamTop10')}>
              <LeaderboardChart data={data.leaderboard} />
            </Panel>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <Panel title={t('pages.dataVision.panel.healthDistribution')}>
                <HealthChart data={data.healthDistribution} />
              </Panel>
              <Panel title={t('pages.dataVision.panel.suggestionSummary')}>
                <SuggestionCards data={data.suggestions} />
              </Panel>
            </div>

            {/* 第二行：趋势图（全宽） */}
            <div
              style={{
                gridColumn: `1 / ${columns + 1}`,
              }}
            >
              <Panel title={t('pages.dataVision.panel.trend30d')}>
                <TrendChart data={data.trend} />
              </Panel>
            </div>
          </div>
        </>
      ) : (
        <div style={{ textAlign: 'center', padding: 40, zIndex: 10, position: 'relative', color: '#ff4d4f' }}>
          {t('pages.dataVision.loadFailed')}
        </div>
      )}

      {/* 底部区域 */}
      <div className="data-vision-footer">
        <span>{t('pages.dataVision.footer')}</span>
      </div>
    </div>
  )
}
