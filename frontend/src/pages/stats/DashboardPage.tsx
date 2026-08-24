import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Grid,
  InputNumber,
  Modal,
  Progress,
  Result,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  AimOutlined,
  BulbOutlined,
  CompassOutlined,
  FundOutlined,
  ReloadOutlined,
  RiseOutlined,
  TeamOutlined,
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

const METHOD_LABELS: Record<string, string> = {
  PHONE: '电话',
  EMAIL: '邮件',
  MEETING: '会议',
  OTHER: '其他',
}

const stageColor: Record<string, string> = {
  INITIAL_CONTACT: 'blue',
  NEGOTIATING: 'gold',
  CLOSED_WON: 'green',
  CLOSED_LOST: 'red',
}

const pct = (v?: number) => (v === undefined || v === null ? '-' : `${Math.round(v * 100)}%`)

/** 统一卡片阴影（现代轻投影，替代无阴影）。 */
const cardShadow = '0 1px 2px rgba(0,0,0,0.04), 0 2px 8px -2px rgba(0,0,0,0.06)'

export default function DashboardPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg
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
  const fu = {
    total: data?.followUps?.total ?? 0,
    byMethod: data?.followUps?.byMethod ?? [],
    recent: data?.followUps?.recent ?? [],
  }
  const fc = {
    weightedAmount: data?.forecast?.weightedAmount,
    breakdown: data?.forecast?.breakdown ?? [],
  }
  const perf = data?.performance
  const funnel = data?.funnel
  const stalled = data?.stalledOpportunities ?? []
  const generatedAt = data?.generatedAt

  const targetMutation = useMutation({
    mutationFn: (amount: number) => saveSalesTarget({ month: currentMonth, targetAmount: amount }),
    onSuccess: async () => {
      message.success('目标已保存')
      setTargetOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['sales-target', currentMonth] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard-stats'] })
    },
    onError: (err) => message.error(extractErrorMessage(err, '保存失败')),
  })

  if (error || (!isLoading && !data)) {
    return (
      <Result
        status="error"
        title="统计数据加载失败"
        extra={
          <Button type="primary" icon={<ReloadOutlined />} onClick={() => void refetch()}>
            重试
          </Button>
        }
      />
    )
  }

  const onSaveTarget = async () => {
    const values = await form.validateFields()
    targetMutation.mutate(values.targetAmount)
  }

  const statCards = [
    {
      title: '商机总数',
      value: s?.opportunityCount ?? 0,
      icon: <TeamOutlined />,
      iconBg: 'linear-gradient(135deg, #1677ff 0%, #69b1ff 100%)',
    },
    {
      title: '金额合计',
      value: formatAmount(s?.amountTotal),
      prefix: '¥',
      icon: <FundOutlined />,
      iconBg: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)',
    },
    {
      title: '赢单率',
      value: pct(s?.winRate),
      icon: <RiseOutlined />,
      iconBg: 'linear-gradient(135deg, #fa8c16 0%, #ffc069 100%)',
    },
    {
      title: '本月新增客户',
      value: s?.newCustomersThisMonth ?? 0,
      icon: <AimOutlined />,
      iconBg: 'linear-gradient(135deg, #722ed1 0%, #b37feb 100%)',
    },
  ]

  const methodMax = Math.max(1, ...(fu.byMethod.map((m) => m.count) ?? [1]))

  const stalledColumns = [
    { title: '商机', dataIndex: 'opportunityName', render: (v?: string) => v ?? '-' },
    { title: '客户', dataIndex: 'customerName', render: (v?: string) => v ?? '-' },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      render: (v?: number) => formatAmount(v),
    },
    {
      title: '阶段',
      dataIndex: 'stage',
      render: (stage: string) => (
        <Tag color={stageColor[stage] ?? 'default'}>
          {STAGE_LABELS[stage as OpportunityStage] ?? stage}
        </Tag>
      ),
    },
    {
      title: '停滞天数',
      dataIndex: 'stalledDays',
      render: (v: number) => <Text type="danger">{v} 天</Text>,
    },
    {
      title: '最后更新',
      dataIndex: 'lastUpdatedAt',
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 19) : '-'),
    },
  ]

  return (
    <div>
      {/* 页面头部：个性化欢迎 + 日期 + 刷新 */}
      <div
        style={{
          marginBottom: 16,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <Title level={3} style={{ marginBottom: 4, fontWeight: 600 }}>
            你好，{user?.displayName ?? user?.username} 👋
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
            {dayjs().format('YYYY 年 M 月 D 日 · dddd')} · 今日销售概览
          </Paragraph>
        </div>
        <Space>
          {/* 029：员工使用地图入口 */}
          <Button icon={<CompassOutlined />} onClick={() => navigate('/usage-map')}>
            查看使用地图
          </Button>
          <Button icon={<ReloadOutlined />} loading={isFetching} onClick={() => void refetch()}>
            刷新
          </Button>
        </Space>
      </div>

      {/* 027：移动端外勤快捷入口 */}
      {isMobile && (
        <Card
          style={{ borderRadius: 12, marginBottom: 16, boxShadow: cardShadow }}
          styles={{ body: { padding: 12 } }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
            <Button
              size="large"
              icon={<TeamOutlined />}
              style={{ height: 52 }}
              onClick={() => navigate('/customers')}
            >
              看客户
            </Button>
            <Button
              size="large"
              icon={<FundOutlined />}
              style={{ height: 52 }}
              onClick={() => navigate('/opportunities')}
            >
              看商机
            </Button>
            <Button
              size="large"
              icon={<AimOutlined />}
              style={{ height: 52 }}
              onClick={() => navigate('/leads')}
            >
              看线索
            </Button>
          </div>
        </Card>
      )}

      {/* AI 智能建议条：琥珀渐变 + 图标卡 + 4 统计点 */}
      <Card
        style={{
          borderRadius: 12,
          marginBottom: 16,
          cursor: 'pointer',
          background: 'linear-gradient(120deg, #fffbe6 0%, #fff7e6 100%)',
          border: '1px solid #ffe7ba',
          boxShadow: cardShadow,
          transition: 'box-shadow 0.2s ease',
        }}
        styles={{ body: { padding: '14px 20px' } }}
        onClick={() => navigate('/suggestions')}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                background: 'linear-gradient(135deg, #faad14 0%, #ffd666 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 18,
                color: '#fff',
                flexShrink: 0,
              }}
            >
              <BulbOutlined />
            </div>
            <div>
              <Typography.Text strong style={{ fontSize: 14 }}>
                AI 智能建议
              </Typography.Text>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  点击查看详情
                </Typography.Text>
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 28, flexWrap: 'wrap' }}>
            <Typography.Text type="secondary">
              流失预警 <b style={{ color: '#cf1322' }}>{suggestionSummary?.atRiskCustomers ?? 0}</b>
            </Typography.Text>
            <Typography.Text type="secondary">
              商机停滞 <b style={{ color: '#fa8c16' }}>{suggestionSummary?.stalledOpportunities ?? 0}</b>
            </Typography.Text>
            <Typography.Text type="secondary">
              待跟进 <b style={{ color: '#1677ff' }}>{suggestionSummary?.followUpCustomers ?? 0}</b>
            </Typography.Text>
            <Typography.Text type="secondary">
              高分线索 <b style={{ color: '#52c41a' }}>{suggestionSummary?.highScoreLeads ?? 0}</b>
            </Typography.Text>
          </div>
        </div>
      </Card>

      {/* KPI 指标卡：白底 + 彩色渐变图标容器（统一品牌感） */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        {statCards.map((card) => (
          <Col xs={24} sm={12} md={6} key={card.title}>
            <Card
              loading={isLoading}
              style={{ borderRadius: 12, boxShadow: cardShadow, height: '100%' }}
              styles={{ body: { padding: 20 } }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <div
                  style={{
                    width: 52,
                    height: 52,
                    borderRadius: 14,
                    background: card.iconBg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 24,
                    color: '#fff',
                    flexShrink: 0,
                    boxShadow: '0 4px 10px rgba(0,0,0,0.12)',
                  }}
                >
                  {card.icon}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, color: '#8c8c8c', marginBottom: 4 }}>{card.title}</div>
                  <Statistic
                    value={card.value}
                    prefix={card.prefix}
                    valueStyle={{ fontSize: 26, fontWeight: 700 }}
                  />
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            loading={isLoading}
            title="销售漏斗"
            styles={{ body: { padding: 16 } }}
            style={{ borderRadius: 12, boxShadow: cardShadow, height: '100%' }}
          >
            {(funnel?.stages ?? []).length ? (
              (() => {
                const stages = funnel?.stages ?? []
                const maxAmount = Math.max(1, ...stages.map((st) => st.amountTotal ?? 0))
                const grand = funnel?.grandTotal?.amountTotal ?? stages.reduce((a, st) => a + (st.amountTotal ?? 0), 0)
                return stages.map((st, idx) => {
                  const color = stageColor[st.stage] ?? 'blue'
                  const colorMap: Record<string, string> = {
                    blue: '#1677ff',
                    gold: '#fa8c16',
                    green: '#52c41a',
                    red: '#cf1322',
                    default: '#8c8c8c',
                  }
                  const baseColor = colorMap[color] ?? '#1677ff'
                  const widthPct = Math.round(((st.amountTotal ?? 0) / maxAmount) * 100)
                  const sharePct = grand > 0 ? Math.round(((st.amountTotal ?? 0) / grand) * 100) : 0
                  return (
                    <div key={st.stage} style={{ marginBottom: idx === stages.length - 1 ? 0 : 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <Tag color={color}>{STAGE_LABELS[st.stage as OpportunityStage] ?? st.stage}</Tag>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {st.count} 个 · {formatAmount(st.amountTotal)} 元
                          {st.conversionRate != null && ` · 转化 ${Math.round((st.conversionRate ?? 0) * 100)}%`}
                        </Text>
                      </div>
                      <div style={{ height: 14, borderRadius: 7, background: 'rgba(0,0,0,0.04)', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${widthPct}%`,
                            height: '100%',
                            borderRadius: 7,
                            background: `linear-gradient(90deg, ${baseColor} 0%, ${baseColor}cc 100%)`,
                            transition: 'width 0.3s ease',
                          }}
                        />
                      </div>
                      <Text type="secondary" style={{ fontSize: 11, marginTop: 2, display: 'block' }}>
                        占比 {sharePct}%
                      </Text>
                    </div>
                  )
                })
              })()
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无销售机会" />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%' }}>
            <Card
              loading={isLoading}
              title="销售预测"
              style={{ borderRadius: 12, boxShadow: cardShadow, flex: 1, minHeight: 0 }}
              styles={{ body: { padding: 20 } }}
            >
            <Statistic
              title="加权预测总额（元）"
              value={formatAmount(fc.weightedAmount)}
              prefix="¥"
            />
            <div style={{ marginTop: 16 }}>
              {(fc.breakdown ?? [])
                .filter((b) => b.stage !== 'CLOSED_LOST')
                .map((b) => (
                  <div
                    key={b.stage}
                    style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, padding: '4px 0' }}
                  >
                    <span>
                      {STAGE_LABELS[b.stage as OpportunityStage] ?? b.stage}（{Math.round(b.probability * 100)}%）
                      {b.probabilitySource === 'HISTORICAL' && (
                        <Tag color="green" style={{ marginLeft: 6, fontSize: 11 }}>
                          历史校准
                        </Tag>
                      )}
                      {b.probabilitySource === 'DEFAULT' && (
                        <Tag style={{ marginLeft: 6, fontSize: 11 }}>默认概率</Tag>
                      )}
                    </span>
                    <span>{formatAmount(b.weighted)} 元</span>
                  </div>
                ))}
            </div>
          </Card>

          <Card
            loading={isLoading}
            title={
              <span>
                业绩达成（{perf?.month ?? currentMonth}）
                {perf?.personal && (
                  <Tag color="blue" style={{ marginLeft: 8 }}>
                    个人目标
                  </Tag>
                )}
                {isAdmin && (
                  <Button size="small" style={{ marginLeft: 12 }} onClick={() => {
                    form.setFieldsValue({ targetAmount: (perf?.targetAmount ?? 0) / 100 })
                    setTargetOpen(true)
                  }}>
                    设置目标
                  </Button>
                )}
              </span>
            }
            style={{ borderRadius: 12, boxShadow: cardShadow, flex: 1, minHeight: 0 }}
            styles={{ body: { padding: 20 } }}
          >
            {perf?.configured ? (
              <>
                <Statistic
                  title="达成率"
                  value={pct(perf?.achievementRate)}
                  valueStyle={{
                    color:
                      (perf?.achievementRate ?? 0) >= 1
                        ? '#3f8600'
                        : (perf?.achievementRate ?? 0) >= 0.5
                          ? '#fa8c16'
                          : '#cf1322',
                  }}
                />
                <div style={{ marginTop: 12 }}>
                  <Progress
                    percent={Math.round((perf?.achievementRate ?? 0) * 100)}
                    status={(perf?.achievementRate ?? 0) >= 1 ? 'success' : 'active'}
                  />
                </div>
                <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
                  已赢单 {formatAmount(perf?.wonAmount)} 元 / 目标{' '}
                  {formatAmount(perf?.targetAmount)} 元
                </Paragraph>
              </>
            ) : (
              <Empty
                description={
                  isAdmin ? '尚未设置本月目标，点击右上角"设置目标"' : '管理员尚未设置本月目标'
                }
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </Card>
          </div>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%' }}>
            <Card
              loading={isLoading}
              title="客户分析"
              style={{ borderRadius: 12, boxShadow: cardShadow }}
              styles={{ body: { padding: '16px 20px' } }}
            >
              <Row gutter={[16, 16]}>
                <Col xs={24} sm={8}>
                  <Statistic title="客户总数" value={s?.customerCount ?? 0} />
                </Col>
                <Col xs={24} sm={8}>
                  <Statistic title="活跃客户" value={s?.activeCustomerCount ?? 0} />
                </Col>
                <Col xs={24} sm={8}>
                  <Statistic
                    title="本月新增"
                    value={s?.newCustomersThisMonth ?? 0}
                    valueStyle={{ color: '#3f8600' }}
                  />
                </Col>
              </Row>
            </Card>

            <Card
              loading={isLoading}
              title="跟进活动"
              style={{ borderRadius: 12, boxShadow: cardShadow, flex: 1, minHeight: 0 }}
              styles={{ body: { padding: 20 } }}
            >
              <Statistic title="跟进总数" value={fu.total} style={{ marginBottom: 16 }} />
              {(fu.byMethod ?? []).map((m) => (
                <div key={m.method} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '4px 0' }}>
                  <span style={{ width: 40, fontSize: 13 }}>{METHOD_LABELS[m.method] ?? m.method}</span>
                  <Progress
                    percent={Math.round((m.count / methodMax) * 100)}
                    size="small"
                    style={{ flex: 1 }}
                    format={() => `${m.count}`}
                  />
                </div>
              ))}
              {!fu.byMethod.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进记录" />}
            </Card>
            {/* 037：团队公告 */}
            <AnnouncementCard />
          </div>
        </Col>

        <Col xs={24} lg={12}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%' }}>
          <Card
            loading={isLoading}
            title="最近跟进"
            styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }}
            style={{ borderRadius: 12, boxShadow: cardShadow, flex: 1, minHeight: 0 }}
          >
            {(fu.recent ?? []).length === 0 ? (
              <div
                style={{
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: 16,
                }}
              >
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进记录" />
              </div>
            ) : (
              <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
                <Table<{ id: number; method: string; content: string; customerName?: string; followUpBy?: string; createdAt: string }>
                  rowKey="id"
                  size="small"
                  dataSource={fu.recent ?? []}
                  pagination={false}
                  columns={
                [
                  {
                    title: '方式',
                    dataIndex: 'method',
                    width: 70,
                    render: (v: string) => <Tag>{METHOD_LABELS[v] ?? v}</Tag>,
                  },
                  { title: '内容', dataIndex: 'content', ellipsis: true },
                  {
                    title: '客户',
                    dataIndex: 'customerName',
                    width: 110,
                    render: (v?: string) => v ?? '-',
                  },
                  {
                    title: '跟进人',
                    dataIndex: 'followUpBy',
                    width: 120,
                    render: (v?: string) => v ?? '-',
                  },
                  {
                    title: '时间',
                    dataIndex: 'createdAt',
                    width: 150,
                    render: (v?: string) => (v ?? '').replace('T', ' ').slice(0, 16),
                  },
                ] as never
              }
              />
              </div>
            )}
          </Card>

          <Card
            loading={isLoading}
            title="停滞商机预警（超过 7 天未更新）"
            styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }}
            style={{ borderRadius: 12, boxShadow: cardShadow, flex: 1, minHeight: 0 }}
          >
            {stalled.length === 0 ? (
              <div
                style={{
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: 16,
                }}
              >
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无停滞商机，管道健康" />
              </div>
            ) : (
              <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
                <Table<StalledOpportunity>
                  rowKey="id"
                  size="small"
                  dataSource={stalled}
                  columns={stalledColumns as never}
                  pagination={false}
                />
              </div>
            )}
          </Card>
          </div>
        </Col>
      </Row>

      <Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12, marginBottom: 0 }}>
        生成时间：{generatedAt ? generatedAt.replace('T', ' ').slice(0, 19) : '-'}
      </Paragraph>

      <Modal
        title="设置销售目标"
        open={targetOpen}
        onOk={() => void onSaveTarget()}
        onCancel={() => setTargetOpen(false)}
        okText="保存"
        confirmLoading={targetMutation.isPending}
        destroyOnClose
      >
        <Form form={form} name="salesTargetForm" layout="vertical">
          <Form.Item
            name="targetAmount"
            label={`目标金额（元，月份：${currentMonth}）`}
            rules={[{ required: true, message: '请输入目标金额' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="输入目标金额" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
