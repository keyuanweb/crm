import { useState, useMemo, memo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  InputNumber,
  Modal,
  Result,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Timeline,
  type TableProps,
} from 'antd'
import {
  BulbOutlined,
  CompassOutlined,
  FundOutlined,
  ReloadOutlined,
  TeamOutlined,
  UserAddOutlined,
  DollarOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  fetchDashboardStats,
  saveSalesTarget,
} from '../../services/statsService'
import { fetchSuggestionSummary } from '../../services/suggestionService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import AnnouncementCard from '../../components/AnnouncementCard'
import { STAGE_LABELS, formatAmount, type OpportunityStage } from '../../types/opportunity'
import type { StalledOpportunity } from '../../types/stats'

const { Title, Paragraph, Text } = Typography

const stageColor: Record<string, string> = {
  INITIAL_CONTACT: 'blue',
  NEGOTIATING: 'gold',
  CLOSED_WON: 'green',
  CLOSED_LOST: 'red',
}

// ========== 子组件 ==========

/** KPI 指标卡子组件 */
const KpiCard = memo(function KpiCard({
  title,
  value,
  prefix,
  icon,
  iconBg,
  onClick,
  trend,
}: {
  title: string
  value: string | number
  prefix?: string
  icon: React.ReactNode
  iconBg: string
  onClick?: () => void
  trend?: { value: number; label: string }
}) {
  return (
    <Card
      className="kpi-card"
      onClick={onClick}
      styles={{ body: { padding: '20px 24px' } }}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 8, fontWeight: 500 }}>{title}</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            {prefix && <span style={{ fontSize: 18, fontWeight: 600, color: '#8c8c8c' }}>{prefix}</span>}
            <Statistic
              value={value}
              valueStyle={{ fontSize: 28, fontWeight: 700, lineHeight: 1.2 }}
              className="stat-number"
            />
          </div>
          {trend && (
            <div style={{ marginTop: 8, fontSize: 12, color: trend.value >= 0 ? '#52c41a' : '#ff4d4f' }}>
              {trend.value >= 0 ? '↑' : '↓'} {Math.abs(trend.value)}% {trend.label}
            </div>
          )}
        </div>
        <div
          className="kpi-icon-wrapper"
          style={{
            background: iconBg,
            width: 48,
            height: 48,
            borderRadius: 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 22,
          }}
        >
          {icon}
        </div>
      </div>
    </Card>
  )
})

/** 销售漏斗子组件 - 重新设计 */
const FunnelChart = memo(function FunnelChart({
  stages,
  grandTotal,
  t,
}: {
  stages: import('../../types/stats').FunnelStageStat[]
  maxAmount: number
  grandTotal: number
  t: (key: string, params?: Record<string, unknown>) => string
}) {
  const colorMap: Record<string, string> = {
    blue: '#1677ff',
    gold: '#fa8c16',
    green: '#52c41a',
    red: '#cf1322',
    default: '#8c8c8c',
  }

  if (stages.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.dashboard.funnel.empty')} />
  }

  const totalCount = stages.reduce((sum, s) => sum + (s.count ?? 0), 0)

  return (
    <div>
      {/* 顶部汇总统计 */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 8,
        marginBottom: 10,
        padding: '8px 12px',
        background: 'linear-gradient(135deg, #f0f5ff 0%, #e6f4ff 100%)',
        borderRadius: 8,
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 10, color: '#8c8c8c', marginBottom: 2 }}>{t('pages.dashboard.funnel.totalOpportunities')}</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: '#1677ff' }}>{totalCount}</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 10, color: '#8c8c8c', marginBottom: 2 }}>{t('pages.dashboard.funnel.totalAmount')}</div>
          <div style={{ fontSize: 15, fontWeight: 700, color: '#1677ff' }}>{formatAmount(grandTotal)}</div>
        </div>
      </div>

      {/* 漏斗阶段列表：1行4列 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
        {stages.map((st) => {
          const color = stageColor[st.stage] ?? 'blue'
          const baseColor = colorMap[color] ?? '#1677ff'
          const countPct = totalCount > 0 ? Math.round(((st.count ?? 0) / totalCount) * 100) : 0
          const amountPct = grandTotal > 0 ? Math.round(((st.amountTotal ?? 0) / grandTotal) * 100) : 0

          return (
            <div
              key={st.stage}
              style={{
                padding: '8px 10px',
                borderRadius: 8,
                background: '#fff',
                border: `1px solid ${baseColor}15`,
                boxShadow: `0 1px 4px ${baseColor}08`,
                transition: 'all 0.3s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = `0 2px 8px ${baseColor}18`
                e.currentTarget.style.transform = 'translateY(-1px)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = `0 1px 4px ${baseColor}08`
                e.currentTarget.style.transform = 'translateY(0)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <div style={{
                    width: 5,
                    height: 5,
                    borderRadius: '50%',
                    background: baseColor,
                    flexShrink: 0,
                  }} />
                  <span style={{ fontSize: 11, fontWeight: 600, color: '#1f1f1f', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {STAGE_LABELS[st.stage as OpportunityStage] ?? st.stage}
                  </span>
                </div>
                <div style={{ fontSize: 16, fontWeight: 700, color: baseColor }}>
                  {st.count}
                </div>
              </div>

              {/* 双进度条：数量 + 金额 */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ fontSize: 9, color: '#8c8c8c', width: 30, flexShrink: 0 }}>{t('pages.dashboard.funnel.quantity')}</span>
                  <div style={{ flex: 1, height: 3, borderRadius: 2, background: '#f5f5f5', overflow: 'hidden' }}>
                    <div style={{
                      width: `${countPct}%`,
                      height: '100%',
                      borderRadius: 2,
                      background: `linear-gradient(90deg, ${baseColor} 0%, ${baseColor}99 100%)`,
                      transition: 'width 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
                    }} />
                  </div>
                  <span style={{ fontSize: 9, color: '#8c8c8c', width: 24, textAlign: 'right', flexShrink: 0 }}>{countPct}%</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ fontSize: 9, color: '#8c8c8c', width: 30, flexShrink: 0 }}>{t('pages.dashboard.funnel.amount')}</span>
                  <div style={{ flex: 1, height: 3, borderRadius: 2, background: '#f5f5f5', overflow: 'hidden' }}>
                    <div style={{
                      width: `${amountPct}%`,
                      height: '100%',
                      borderRadius: 2,
                      background: `linear-gradient(90deg, ${baseColor} 0%, ${baseColor}99 100%)`,
                      transition: 'width 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
                    }} />
                  </div>
                  <span style={{ fontSize: 9, color: '#8c8c8c', width: 24, textAlign: 'right', flexShrink: 0 }}>{amountPct}%</span>
                </div>
              </div>

              <div style={{ marginTop: 4, fontSize: 10, color: '#8c8c8c', textAlign: 'right' }}>
                {formatAmount(st.amountTotal)}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
})

/** 业绩达成子组件 - 重新设计 */
const PerformanceCard = memo(function PerformanceCard({
  perf,
  isAdmin,
  onSetTarget,
  t,
}: {
  perf: import('../../types/stats').DashboardPerformance | undefined
  isAdmin: boolean
  onSetTarget: () => void
  t: (key: string, params?: Record<string, unknown>) => string
}) {
  if (!perf?.configured) {
    return (
      <Empty
        description={
          isAdmin ? t('pages.dashboard.performance.notConfiguredAdmin') : t('pages.dashboard.performance.notConfiguredUser')
        }
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  const achievementRate = (perf?.achievementRate ?? 0) * 100
  const progressColor = achievementRate >= 100 ? '#52c41a' : achievementRate >= 50 ? '#faad14' : '#ff4d4f'
  const progressBg = achievementRate >= 100 ? '#f6ffed' : achievementRate >= 50 ? '#fff7e6' : '#fff2f0'

  return (
    <div>
      {/* 头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: '#1f1f1f' }}>
          {t('pages.dashboard.performance.title')}
        </span>
        <Space size={4}>
          {perf?.personal && (
            <Tag color="blue" style={{ fontSize: 10, borderRadius: 4 }}>
              {t('pages.dashboard.performance.personalTarget')}
            </Tag>
          )}
          {isAdmin && (
            <Button size="small" type="primary" onClick={onSetTarget} style={{ fontSize: 10, height: 22 }}>
              {t('pages.dashboard.buttons.setTarget')}
            </Button>
          )}
        </Space>
      </div>

      {/* 环形进度指示器 */}
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: 12,
        position: 'relative',
      }}>
        <div style={{
          width: 80,
          height: 80,
          borderRadius: '50%',
          background: `conic-gradient(${progressColor} 0% ${achievementRate}%, #f0f0f0 ${achievementRate}% 100%)`,
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          position: 'relative',
        }}>
          <div style={{
            width: 62,
            height: 62,
            borderRadius: '50%',
            background: '#fff',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
          }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: progressColor, lineHeight: 1 }}>
              {Math.round(achievementRate)}
            </div>
            <div style={{ fontSize: 10, color: '#8c8c8c', marginTop: 1 }}>%</div>
          </div>
        </div>
      </div>

      {/* 关键指标网格 */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 8,
        marginBottom: 8,
      }}>
        <div style={{
          padding: 8,
          borderRadius: 6,
          background: progressBg,
          border: `1px solid ${progressColor}20`,
        }}>
          <div style={{ fontSize: 10, color: '#8c8c8c', marginBottom: 2 }}>{t('pages.dashboard.performance.wonAmount')}</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: progressColor }}>
            {formatAmount(perf?.wonAmount)}
          </div>
        </div>
        <div style={{
          padding: 8,
          borderRadius: 6,
          background: '#f5f5f5',
          border: '1px solid #e8e8e8',
        }}>
          <div style={{ fontSize: 10, color: '#8c8c8c', marginBottom: 2 }}>{t('pages.dashboard.performance.target')}</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: '#595959' }}>
            {formatAmount(perf?.targetAmount)}
          </div>
        </div>
      </div>

      {/* 剩余目标 */}
      <div style={{
        padding: 6,
        borderRadius: 6,
        background: achievementRate >= 100 ? '#f6ffed' : '#fff7e6',
        border: `1px solid ${achievementRate >= 100 ? '#52c41a20' : '#faad1420'}`,
        textAlign: 'center',
      }}>
        <span style={{ fontSize: 11, color: '#595959' }}>
          {achievementRate >= 100 ? `${t('pages.dashboard.performance.achieved')} ✓` : `${t('pages.dashboard.performance.remaining')} `}
          {formatAmount(Math.max(0, (perf?.targetAmount ?? 0) - (perf?.wonAmount ?? 0)))} {t('pages.dashboard.funnel.amountSuffix')}
        </span>
      </div>
    </div>
  )
})

/** 待办事项子组件 */
const TodoList = memo(function TodoList({
  todos,
  t,
  onTodoClick,
}: {
  todos: Array<{ id: number; type: string; title: string; deadline?: string; priority: 'high' | 'medium' | 'low' }>
  t: (key: string, params?: Record<string, unknown>) => string
  onTodoClick: (todo: typeof todos[0]) => void
}) {
  const priorityColor = { high: 'red', medium: 'orange', low: 'blue' }
  
  if (todos.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.dashboard.todo.empty')} />
  }

  return (
    <div>
      <Timeline
        items={todos.map((todo) => ({
          color: priorityColor[todo.priority],
          children: (
            <div onClick={() => onTodoClick(todo)} style={{ cursor: 'pointer' }}>
              <div style={{ fontWeight: 500, marginBottom: 4 }}>{todo.title}</div>
              <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                {todo.deadline && `${t('pages.dashboard.todo.deadline')}：${todo.deadline}`}
              </div>
            </div>
          ),
        }))}
      />
    </div>
  )
})

/** 活动动态子组件 */
const ActivityFeed = memo(function ActivityFeed({
  activities,
  t,
}: {
  activities: Array<{ id: number; type: string; content: string; user: string; createdAt: string }>
  t: (key: string, params?: Record<string, unknown>) => string
}) {
  if (activities.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.dashboard.activity.empty')} />
  }

  return (
    <Timeline
      items={activities.slice(0, 10).map((activity) => ({
        color: '#1677ff',
        children: (
          <div>
            <div>
              <Text strong>{activity.user}</Text>
              <Text> {activity.content}</Text>
            </div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
              {dayjs(activity.createdAt).format('YYYY-MM-DD HH:mm')}
            </div>
          </div>
        ),
      }))}
    />
  )
})

export default function DashboardPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const currentMonth = dayjs().format('YYYY-MM')
  const [targetOpen, setTargetOpen] = useState(false)
  const [form] = Form.useForm<{ targetAmount: number }>()

  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: fetchDashboardStats,
  })

  // 022：AI 智能建议摘要
  const { data: suggestionSummary } = useQuery({
    queryKey: ['suggestion-summary'],
    queryFn: fetchSuggestionSummary,
  })

  // 安全取值：接口缺字段时避免整页崩溃（空白页）
  const s = data?.summary
  const perf = data?.performance
  const funnel = data?.funnel
  const stalled = data?.stalledOpportunities ?? []
  const generatedAt = data?.generatedAt

  const targetMutation = useMutation({
    mutationFn: (amount: number) => saveSalesTarget({ month: currentMonth, targetAmount: amount }),
    onSuccess: async () => {
      message.success(t('pages.dashboard.modal.saved'))
      setTargetOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['sales-target', currentMonth] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] })
    },
    onError: (err) => message.error(extractErrorMessage(err, t('pages.dashboard.modal.saveFailed'))),
  })

  // AI 建议数据（hooks 必须在 early return 之前调用，否则渲染次数不一致）
  const aiStats = useMemo(() => [
    {
      label: t('pages.dashboard.aiSuggestions.atRiskCustomers'),
      value: suggestionSummary?.atRiskCustomers ?? 0,
      color: '#cf1322',
      bg: '#fff1f0',
    },
    {
      label: t('pages.dashboard.aiSuggestions.stalledOpportunities'),
      value: suggestionSummary?.stalledOpportunities ?? 0,
      color: '#fa8c16',
      bg: '#fff7e6',
    },
    {
      label: t('pages.dashboard.aiSuggestions.followUpCustomers'),
      value: suggestionSummary?.followUpCustomers ?? 0,
      color: '#1677ff',
      bg: '#e6f4ff',
    },
    {
      label: t('pages.dashboard.aiSuggestions.highScoreLeads'),
      value: suggestionSummary?.highScoreLeads ?? 0,
      color: '#52c41a',
      bg: '#f6ffed',
    },
  ], [suggestionSummary, t])

  // 模拟待办数据（实际应从 API 获取）
  const todos = useMemo(() => [
    { id: 1, type: 'approval', title: t('pages.dashboard.todo.mockApproval'), deadline: '2026-08-30', priority: 'high' as const },
    { id: 2, type: 'followup', title: t('pages.dashboard.todo.mockFollowup'), deadline: '2026-08-31', priority: 'medium' as const },
    { id: 3, type: 'task', title: t('pages.dashboard.todo.mockTask'), deadline: '2026-09-01', priority: 'medium' as const },
  ], [t])

  // 模拟活动动态（实际应从 API 获取）
  const activities = useMemo(() => [
    { id: 1, type: 'customer', content: t('pages.dashboard.activity.mockCustomer'), user: t('pages.dashboard.activity.userZhangSan'), createdAt: dayjs().subtract(10, 'minute').toISOString() },
    { id: 2, type: 'opportunity', content: t('pages.dashboard.activity.mockOpportunity'), user: t('pages.dashboard.activity.userLiSi'), createdAt: dayjs().subtract(30, 'minute').toISOString() },
    { id: 3, type: 'contract', content: t('pages.dashboard.activity.mockContract'), user: t('pages.dashboard.activity.userWangWu'), createdAt: dayjs().subtract(2, 'hour').toISOString() },
    { id: 4, type: 'task', content: t('pages.dashboard.activity.mockTask'), user: t('pages.dashboard.activity.userZhaoLiu'), createdAt: dayjs().subtract(4, 'hour').toISOString() },
  ], [t])

  if (error || (!isLoading && !data)) {
    return (
      <Result
        status="error"
        title={t('pages.dashboard.error.loadFailed')}
        extra={
          <Button type="primary" icon={<ReloadOutlined />} onClick={() => void refetch()}>
            {t('pages.dashboard.error.retry')}
          </Button>
        }
      />
    )
  }

  const onSaveTarget = async () => {
    const values = await form.validateFields()
    targetMutation.mutate(values.targetAmount)
  }

  // 漏斗数据
  const funnelStages = funnel?.stages ?? []
  const funnelMaxAmount = Math.max(1, ...funnelStages.map((st) => st.amountTotal ?? 0))
  const funnelGrandTotal = funnel?.grandTotal?.amountTotal ?? funnelStages.reduce((a, st) => a + (st.amountTotal ?? 0), 0)

  const stalledColumns = [
    { title: t('pages.dashboard.stalledOpportunities.opportunity'), dataIndex: 'opportunityName', width: 150, render: (v?: string) => v ?? '-' },
    { title: t('pages.dashboard.stalledOpportunities.customer'), dataIndex: 'customerName', width: 130, render: (v?: string) => v ?? '-' },
    {
      title: t('pages.dashboard.stalledOpportunities.amount'),
      dataIndex: 'amount',
      width: 120,
      render: (v?: number) => <span style={{ fontWeight: 500 }}>{formatAmount(v)}</span>,
    },
    {
      title: t('pages.dashboard.stalledOpportunities.stage'),
      dataIndex: 'stage',
      width: 100,
      render: (stage: string) => (
        <Tag color={stageColor[stage] ?? 'default'} className="dashboard-tag">
          {STAGE_LABELS[stage as OpportunityStage] ?? stage}
        </Tag>
      ),
    },
    {
      title: t('pages.dashboard.stalledOpportunities.stalledDays'),
      dataIndex: 'stalledDays',
      width: 100,
      render: (v: number) => (
        <Tag color={v > 30 ? 'red' : v > 14 ? 'orange' : 'gold'} className="dashboard-tag">
          {v} {t('pages.dashboard.stalledOpportunities.days')}
        </Tag>
      ),
    },
    {
      title: t('pages.dashboard.stalledOpportunities.lastUpdated'),
      dataIndex: 'lastUpdatedAt',
      width: 150,
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 19) : '-'),
    },
  ]

  return (
    <div className="dashboard-header">
      {/* 页面头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 24 }}>
        <div>
          <Title level={3} className="dashboard-welcome-title">
            {t('home.greeting', { name: user?.displayName ?? user?.username })}
          </Title>
          <Paragraph className="dashboard-welcome-sub" style={{ marginBottom: 0 }}>
            {dayjs().format(t('home.dateFormat'))} · {t('home.today')}
          </Paragraph>
        </div>
        <Space>
          <Button icon={<CompassOutlined />} onClick={() => navigate('/usage-map')}>
            {t('pages.dashboard.buttons.viewUsageMap')}
          </Button>
          <Button icon={<ReloadOutlined />} loading={isFetching} onClick={() => void refetch()}>
            {t('pages.dashboard.buttons.refresh')}
          </Button>
        </Space>
      </div>

      {/* KPI 指标卡行 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {isLoading ? (
          <>
            {[0, 1, 2, 3].map((i) => (
              <Col xs={24} sm={12} md={6} key={i}>
                <Card styles={{ body: { padding: '20px 24px' } }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                    <div className="kpi-icon-wrapper skeleton" />
                    <div style={{ flex: 1 }}>
                      <div className="skeleton" style={{ height: 14, width: '60%', marginBottom: 12 }} />
                      <div className="skeleton" style={{ height: 28, width: '40%' }} />
                    </div>
                  </div>
                </Card>
              </Col>
            ))}
          </>
        ) : (
          <>
            <Col xs={24} sm={12} md={6}>
              <KpiCard
                title={t('pages.dashboard.statCards.totalCustomers')}
                value={s?.customerCount ?? 0}
                icon={<TeamOutlined />}
                iconBg="linear-gradient(135deg, #1677ff 0%, #69b1ff 100%)"
                onClick={() => navigate('/customers')}
                trend={{ value: 5, label: t('home.trendVsLastMonth') }}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <KpiCard
                title={t('pages.dashboard.statCards.activeOpportunities')}
                value={s?.opportunityCount ?? 0}
                icon={<FundOutlined />}
                iconBg="linear-gradient(135deg, #52c41a 0%, #95de64 100%)"
                onClick={() => navigate('/opportunities')}
                trend={{ value: 8, label: t('home.trendVsLastMonth') }}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <KpiCard
                title={t('pages.dashboard.statCards.amountTotal')}
                value={formatAmount(s?.amountTotal)}
                prefix={t('home.currency')}
                icon={<DollarOutlined />}
                iconBg="linear-gradient(135deg, #fa8c16 0%, #ffc069 100%)"
                trend={{ value: 12, label: t('home.trendVsLastMonth') }}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <KpiCard
                title={t('pages.dashboard.statCards.newCustomersThisMonth')}
                value={s?.newCustomersThisMonth ?? 0}
                icon={<UserAddOutlined />}
                iconBg="linear-gradient(135deg, #722ed1 0%, #b37feb 100%)"
                onClick={() => navigate('/customers')}
                trend={{ value: 3, label: t('home.trendVsLastMonth') }}
              />
            </Col>
          </>
        )}
      </Row>

      {/* AI 智能建议条 */}
      <Card
        className="ai-suggestion-card"
        styles={{ body: { padding: '16px 24px' } }}
        style={{ marginBottom: 24 }}
        onClick={() => navigate('/suggestions')}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div
              className="ai-icon-pulse"
              style={{
                width: 42,
                height: 42,
                borderRadius: 12,
                background: 'linear-gradient(135deg, #faad14 0%, #ffd666 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 20,
                color: '#fff',
                flexShrink: 0,
              }}
            >
              <BulbOutlined />
            </div>
            <div>
              <Typography.Text strong style={{ fontSize: 15, color: '#1f1f1f' }}>
                {t('pages.dashboard.aiSuggestions.title')}
              </Typography.Text>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {t('pages.dashboard.aiSuggestions.clickDetail')}
                </Typography.Text>
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {aiStats.map((stat) => (
              <div
                key={stat.label}
                style={{
                  padding: '6px 14px',
                  borderRadius: 8,
                  background: stat.bg,
                  border: `1px solid ${stat.color}20`,
                }}
              >
                <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>{stat.label}</div>
                <div style={{ fontSize: 18, fontWeight: 700, color: stat.color }}>{stat.value}</div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* 主内容区：2x2 网格布局 */}
      <Row gutter={[20, 24]}>
        {/* 第一行：销售漏斗 + 业绩达成 */}
        <Col xs={24} lg={12}>
          <Card
            className="dashboard-card"
            loading={isLoading}
            title={t('pages.dashboard.funnel.title')}
            styles={{ body: { padding: 20 } }}
            style={{ borderRadius: 12, height: '100%' }}
          >
            {funnelStages.length ? (
              <FunnelChart
                stages={funnelStages}
                maxAmount={funnelMaxAmount}
                grandTotal={funnelGrandTotal}
                t={t}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.dashboard.funnel.empty')} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            className="dashboard-card"
            loading={isLoading}
            title={t('pages.dashboard.performance.title')}
            styles={{ body: { padding: 20 } }}
            style={{ borderRadius: 12, height: '100%' }}
          >
            {isLoading ? (
              <>
                <div className="skeleton" style={{ height: 20, width: '50%', marginBottom: 16 }} />
                <div className="skeleton" style={{ height: 32, width: '30%', marginBottom: 16 }} />
                <div className="skeleton" style={{ height: 16, width: '100%' }} />
              </>
            ) : (
              <PerformanceCard
                perf={perf}
                isAdmin={isAdmin}
                onSetTarget={() => {
                  form.setFieldsValue({ targetAmount: (perf?.targetAmount ?? 0) / 100 })
                  setTargetOpen(true)
                }}
                t={t}
              />
            )}
          </Card>
        </Col>

        {/* 第二行：待办事项 + 活动动态 */}
        <Col xs={24} lg={12}>
          <Card
            className="dashboard-card"
            loading={isLoading}
            title={t('pages.dashboard.todo.title')}
            styles={{ body: { padding: 20 } }}
            style={{ borderRadius: 12, height: '100%' }}
          >
            {isLoading ? (
              <div style={{ padding: 20 }}>
                {[0, 1, 2].map((i) => (
                  <div key={i} style={{ marginBottom: 12 }}>
                    <div className="skeleton" style={{ height: 14, width: '80%', marginBottom: 8 }} />
                    <div className="skeleton" style={{ height: 14, width: '60%' }} />
                  </div>
                ))}
              </div>
            ) : (
              <TodoList
                todos={todos}
                t={t}
                onTodoClick={(todo) => {
                  if (todo.type === 'approval') navigate('/approvals')
                  else if (todo.type === 'followup') navigate('/opportunities')
                  else navigate('/tasks')
                }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            className="dashboard-card"
            loading={isLoading}
            title={t('pages.dashboard.activity.title')}
            styles={{ body: { padding: 20 } }}
            style={{ borderRadius: 12, height: '100%' }}
          >
            {isLoading ? (
              <div style={{ padding: 20 }}>
                {[0, 1, 2, 3].map((i) => (
                  <div key={i} style={{ marginBottom: 12 }}>
                    <div className="skeleton" style={{ height: 14, width: '80%', marginBottom: 8 }} />
                    <div className="skeleton" style={{ height: 14, width: '60%' }} />
                  </div>
                ))}
              </div>
            ) : (
              <ActivityFeed activities={activities} t={t} />
            )}
          </Card>
        </Col>
      </Row>

      {/* 停滞商机预警 + 团队公告 */}
      <Row gutter={[20, 24]} style={{ marginTop: 24 }}>
        {/* 左列：停滞商机预警 */}
        <Col xs={24} lg={12}>
          <Card
            className="dashboard-card"
            loading={isLoading}
            title={t('pages.dashboard.stalledOpportunities.title')}
            styles={{ body: { padding: 0 } }}
            style={{ borderRadius: 12, height: '100%' }}
          >
            {isLoading ? (
              <div style={{ padding: 20 }}>
                {[0, 1, 2].map((i) => (
                  <div key={i} style={{ marginBottom: 12 }}>
                    <div className="skeleton" style={{ height: 14, width: '70%', marginBottom: 8 }} />
                    <div className="skeleton" style={{ height: 14, width: '50%' }} />
                  </div>
                ))}
              </div>
            ) : (
              <StalledTable stalled={stalled} columns={stalledColumns} t={t} />
            )}
          </Card>
        </Col>

        {/* 右列：团队公告 */}
        <Col xs={24} lg={12}>
          <AnnouncementCard />
        </Col>
      </Row>

      {/* 底部信息 */}
      <Paragraph type="secondary" style={{ marginTop: 20, fontSize: 12, marginBottom: 0, textAlign: 'center' }}>
        {t('pages.dashboard.generatedAt')}：{generatedAt ? generatedAt.replace('T', ' ').slice(0, 19) : '-'}
      </Paragraph>

      {/* 设置目标弹窗 */}
      <Modal
        title={t('pages.dashboard.modal.title')}
        open={targetOpen}
        onOk={() => void onSaveTarget()}
        onCancel={() => setTargetOpen(false)}
        okText={t('pages.dashboard.modal.save')}
        confirmLoading={targetMutation.isPending}
        destroyOnClose
      >
        <Form form={form} name="salesTargetForm" layout="vertical">
          <Form.Item
            name="targetAmount"
            label={t('pages.dashboard.modal.targetAmount', { month: currentMonth })}
            rules={[{ required: true, message: t('pages.dashboard.modal.amountRequired') }]}
          >
            <InputNumber<number>
              min={0}
              precision={2}
              style={{ width: '100%' }}
              placeholder={t('pages.dashboard.modal.placeholder')}
              formatter={(value) => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
              parser={(value) => Number(String(value ?? '').replace(/\$\s?|(,*)/g, '')) || 0}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

/** 停滞商机表格子组件 */
const StalledTable = memo(function StalledTable({
  stalled,
  columns,
  t,
}: {
  stalled: StalledOpportunity[]
  columns: TableProps<StalledOpportunity>['columns']
  t: (key: string, params?: Record<string, unknown>) => string
}) {
  if (stalled.length === 0) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '32px 0' }}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.dashboard.stalledOpportunities.empty')} />
      </div>
    )
  }

  return (
    <Table<StalledOpportunity>
      className="dashboard-table"
      rowKey="id"
      size="small"
      dataSource={stalled}
      pagination={false}
      columns={columns}
      onRow={() => ({
        style: { cursor: 'pointer' },
      })}
    />
  )
})
