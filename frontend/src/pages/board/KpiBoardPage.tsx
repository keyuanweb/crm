import { useEffect, useRef, useState } from 'react'
import { Button, Tag, Typography } from 'antd'
import { FullscreenExitOutlined, FullscreenOutlined } from '@ant-design/icons'
import { fetchKpiBoard } from '../../services/kpiBoardService'
import { formatAmount } from '../../types/opportunity'
import type { KpiBoard, TrendPoint } from '../../types/kpiBoard'

const { Text } = Typography

const STAGE_LABELS: Record<string, string> = {
  INITIAL_CONTACT: '初步接触',
  NEGOTIATING: '谈判中',
  CLOSED_WON: '已赢单',
  CLOSED_LOST: '已输单',
}

const STAGE_COLORS: Record<string, string> = {
  INITIAL_CONTACT: '#1677ff',
  NEGOTIATING: '#fa8c16',
  CLOSED_WON: '#52c41a',
  CLOSED_LOST: '#cf1322',
}

/** 深色面板容器。 */
const Panel = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <div
    style={{
      background: 'rgba(13, 32, 66, 0.85)',
      border: '1px solid rgba(64, 128, 255, 0.3)',
      borderRadius: 12,
      padding: '16px 18px',
      boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    }}
  >
    <div style={{ color: '#7db4ff', fontSize: 14, fontWeight: 600, marginBottom: 12, letterSpacing: 1 }}>
      {title}
    </div>
    <div style={{ flex: 1, minHeight: 0 }}>{children}</div>
  </div>
)

/** SVG 折线趋势图。 */
const TrendChart = ({ points }: { points: TrendPoint[] }) => {
  if (!points.length) {
    return <Text style={{ color: '#5a7cb8' }}>暂无趋势数据</Text>
  }
  const w = 420
  const h = 140
  const pad = 12
  const maxAmount = Math.max(...points.map((p) => p.amount), 1)
  const step = points.length > 1 ? (w - pad * 2) / (points.length - 1) : 0
  const coords = points.map((p, i) => ({
    x: pad + i * step,
    y: h - pad - (p.amount / maxAmount) * (h - pad * 2),
  }))
  const path = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
  return (
    <svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none">
      <path d={path} fill="none" stroke="#52c41a" strokeWidth={2} />
      {coords.map((c, i) => (
        <circle key={i} cx={c.x} cy={c.y} r={3} fill="#52c41a" />
      ))}
      <text x={pad} y={h - 2} fill="#5a7cb8" fontSize={9}>
        {points[0].date}
      </text>
      <text x={w - 60} y={h - 2} fill="#5a7cb8" fontSize={9}>
        {points[points.length - 1].date}
      </text>
    </svg>
  )
}

export default function KpiBoardPage() {
  const [data, setData] = useState<KpiBoard | null>(null)
  const [error, setError] = useState('')
  const [isFullscreen, setIsFullscreen] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = async () => {
    try {
      const d = await fetchKpiBoard()
      setData(d)
      setError('')
    } catch {
      setError('刷新失败，保留上次数据')
    }
  }

  useEffect(() => {
    void load()
    timerRef.current = setInterval(() => void load(), 60000)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [])

  const toggleFullscreen = () => {
    if (document.fullscreenElement) {
      void document.exitFullscreen()
    } else {
      void document.documentElement.requestFullscreen()
    }
  }

  useEffect(() => {
    const onFsChange = () => setIsFullscreen(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', onFsChange)
    return () => document.removeEventListener('fullscreenchange', onFsChange)
  }, [])

  const kpi = data?.kpi
  const maxStageAmount = Math.max(
    1,
    ...(data?.funnel?.stages ?? []).map((s) => s.amountTotal),
  )
  const now = new Date()
  const nowStr = now.toLocaleString('zh-CN', { hour12: false })

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'radial-gradient(ellipse at top, #122a52 0%, #0a1830 60%, #060d1e 100%)',
        color: '#e6f0ff',
        padding: 24,
      }}
    >
      {/* 顶部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: 4, background: 'linear-gradient(90deg,#4da3ff,#7dd3fc)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            销售经营数据大屏
          </div>
          <Text style={{ color: '#5a7cb8', fontSize: 12 }}>
            数据自动刷新（60 秒）{error && ` · ${error}`}
          </Text>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Text style={{ color: '#7db4ff', fontSize: 14 }}>{nowStr}</Text>
          <Button
            size="small"
            icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            onClick={toggleFullscreen}
          >
            {isFullscreen ? '退出全屏' : '全屏'}
          </Button>
        </div>
      </div>

      {/* KPI 卡行 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 14, marginBottom: 16 }}>
        {[
          { label: '商机总数', value: kpi?.opportunityCount ?? 0, color: '#4da3ff' },
          { label: '金额合计（元）', value: formatAmount(kpi?.amountTotal), color: '#7dd3fc' },
          { label: '赢单率', value: kpi ? `${Math.round((kpi.winRate ?? 0) * 100)}%` : '-', color: '#52c41a' },
          { label: '客户总数', value: kpi?.customerCount ?? 0, color: '#fa8c16' },
          { label: '活跃客户', value: kpi?.activeCustomerCount ?? 0, color: '#b37feb' },
          { label: '本月新增客户', value: kpi?.newCustomersThisMonth ?? 0, color: '#ff85c0' },
        ].map((c) => (
          <div
            key={c.label}
            style={{
              background: 'rgba(13,32,66,0.85)',
              border: '1px solid rgba(64,128,255,0.3)',
              borderRadius: 12,
              padding: '14px 16px',
              textAlign: 'center',
            }}
          >
            <div style={{ fontSize: 13, color: '#7db4ff', marginBottom: 6 }}>{c.label}</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: c.color }}>{c.value}</div>
          </div>
        ))}
      </div>

      {/* 主区块：左漏斗 + 中排行 + 右健康度/建议 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14 }}>
        {/* 销售漏斗 */}
        <Panel title="销售漏斗">
          {(data?.funnel?.stages ?? []).length ? (
            data!.funnel.stages.map((s) => (
              <div key={s.stage} style={{ marginBottom: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                  <span>{STAGE_LABELS[s.stage] ?? s.stage}</span>
                  <span style={{ color: '#7db4ff' }}>
                    {s.count} 个 · {formatAmount(s.amountTotal)} 元
                  </span>
                </div>
                <div style={{ height: 12, borderRadius: 6, background: 'rgba(255,255,255,0.08)', overflow: 'hidden' }}>
                  <div
                    style={{
                      width: `${(s.amountTotal / maxStageAmount) * 100}%`,
                      height: '100%',
                      borderRadius: 6,
                      background: `${STAGE_COLORS[s.stage] ?? '#1677ff'}cc`,
                    }}
                  />
                </div>
              </div>
            ))
          ) : (
            <Text style={{ color: '#5a7cb8' }}>暂无销售机会</Text>
          )}
        </Panel>

        {/* 团队排行 Top10 */}
        <Panel title="团队排行 TOP10">
          <div style={{ fontSize: 12 }}>
            {(data?.leaderboard ?? []).length ? (
              data!.leaderboard.map((l, i) => (
                <div key={l.userId} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                  <span style={{ width: 20, color: i < 3 ? '#faad14' : '#7db4ff', fontWeight: 600 }}>{i + 1}</span>
                  <span style={{ flex: 1 }}>{l.displayName}</span>
                  <span style={{ color: '#52c41a' }}>{formatAmount(l.wonAmount)}</span>
                  <Tag color={l.achievementRate == null ? 'default' : l.achievementRate >= 0.8 ? 'green' : l.achievementRate >= 0.5 ? 'gold' : 'red'} style={{ marginInlineEnd: 0 }}>
                    {l.achievementRate == null ? '未设目标' : `${Math.round(l.achievementRate * 100)}%`}
                  </Tag>
                </div>
              ))
            ) : (
              <Text style={{ color: '#5a7cb8' }}>暂无排行数据</Text>
            )}
          </div>
        </Panel>

        {/* 健康度分布 + 建议摘要 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Panel title="客户健康度分布">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, textAlign: 'center' }}>
              <div>
                <div style={{ fontSize: 30, fontWeight: 700, color: '#ff4d4f' }}>{data?.healthDistribution.red ?? 0}</div>
                <div style={{ fontSize: 12, color: '#7db4ff' }}>风险</div>
              </div>
              <div>
                <div style={{ fontSize: 30, fontWeight: 700, color: '#faad14' }}>{data?.healthDistribution.yellow ?? 0}</div>
                <div style={{ fontSize: 12, color: '#7db4ff' }}>关注</div>
              </div>
              <div>
                <div style={{ fontSize: 30, fontWeight: 700, color: '#52c41a' }}>{data?.healthDistribution.green ?? 0}</div>
                <div style={{ fontSize: 12, color: '#7db4ff' }}>健康</div>
              </div>
            </div>
          </Panel>
          <Panel title="智能建议摘要">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 8, fontSize: 13 }}>
              <div>流失预警 <b style={{ color: '#ff4d4f' }}>{data?.suggestions.atRiskCustomers ?? 0}</b></div>
              <div>商机停滞 <b style={{ color: '#fa8c16' }}>{data?.suggestions.stalledOpportunities ?? 0}</b></div>
              <div>待跟进 <b style={{ color: '#4da3ff' }}>{data?.suggestions.followUpCustomers ?? 0}</b></div>
              <div>高分线索 <b style={{ color: '#52c41a' }}>{data?.suggestions.highScoreLeads ?? 0}</b></div>
            </div>
          </Panel>
        </div>
      </div>

      {/* 底部趋势 */}
      <div style={{ marginTop: 14 }}>
        <Panel title="近 30 天商机金额趋势">
          <TrendChart points={data?.trend ?? []} />
        </Panel>
      </div>
    </div>
  )
}
