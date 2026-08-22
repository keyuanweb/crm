import { useState } from 'react'
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
  Progress,
  Result,
  Row,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  AimOutlined,
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
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import { STAGE_LABELS, formatAmount, type OpportunityStage } from '../../types/opportunity'
import type { FunnelStageStat, StalledOpportunity } from '../../types/stats'

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

export default function DashboardPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const currentMonth = dayjs().format('YYYY-MM')
  const [targetOpen, setTargetOpen] = useState(false)
  const [form] = Form.useForm<{ targetAmount: number }>()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: fetchDashboardStats,
  })

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
      value: data?.summary.opportunityCount ?? 0,
      icon: <TeamOutlined />,
      bg: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)',
    },
    {
      title: '金额合计',
      value: formatAmount(data?.summary.amountTotal),
      prefix: '¥',
      icon: <FundOutlined />,
      bg: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)',
    },
    {
      title: '赢单率',
      value: pct(data?.summary.winRate),
      icon: <RiseOutlined />,
      bg: 'linear-gradient(135deg, #fa8c16 0%, #ffc069 100%)',
    },
    {
      title: '本月新增客户',
      value: data?.summary.newCustomersThisMonth ?? 0,
      icon: <AimOutlined />,
      bg: 'linear-gradient(135deg, #722ed1 0%, #b37feb 100%)',
    },
  ]

  const funnelColumns = [
    {
      title: '阶段',
      dataIndex: 'stage',
      render: (stage: string) => (
        <Tag color={stageColor[stage] ?? 'default'}>
          {STAGE_LABELS[stage as OpportunityStage] ?? stage}
        </Tag>
      ),
    },
    { title: '商机数量', dataIndex: 'count' },
    {
      title: '金额合计（元）',
      dataIndex: 'amountTotal',
      render: (v: number) => formatAmount(v),
    },
    {
      title: '转化率',
      dataIndex: 'conversionRate',
      render: (v?: number | null) => (v === null || v === undefined ? '-' : `${Math.round(v * 100)}%`),
    },
  ]

  const methodMax = Math.max(1, ...(data?.followUps.byMethod.map((m) => m.count) ?? [1]))

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
      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={4} style={{ marginBottom: 4 }}>
            销售仪表盘
          </Title>
          <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
            核心指标 · 漏斗 · 预测 · 业绩达成 · 客户与跟进 · 停滞预警（缓存 ≤5 分钟）
          </Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        {statCards.map((card) => (
          <Col xs={24} sm={12} md={6} key={card.title}>
            <Card
              loading={isLoading}
              bodyStyle={{ padding: 20 }}
              style={{ background: card.bg, border: 'none', borderRadius: 10, color: '#fff' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.85)', marginBottom: 8 }}>
                    {card.title}
                  </div>
                  <Statistic
                    value={card.value}
                    prefix={card.prefix}
                    valueStyle={{ color: '#fff', fontSize: 24, fontWeight: 600 }}
                  />
                </div>
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 12,
                    background: 'rgba(255,255,255,0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 22,
                    color: '#fff',
                  }}
                >
                  {card.icon}
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card
            loading={isLoading}
            title="销售漏斗"
            bodyStyle={{ padding: 0 }}
            style={{ borderRadius: 10, marginBottom: 16 }}
          >
            <Table<FunnelStageStat>
              rowKey="stage"
              size="middle"
              dataSource={data?.funnel.stages ?? []}
              columns={funnelColumns as never}
              pagination={false}
              locale={{ emptyText: '暂无销售机会' }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card
            loading={isLoading}
            title="销售预测"
            style={{ borderRadius: 10, marginBottom: 16 }}
            bodyStyle={{ padding: 20 }}
          >
            <Statistic
              title="加权预测总额（元）"
              value={formatAmount(data?.forecast.weightedAmount)}
              prefix="¥"
            />
            <div style={{ marginTop: 16 }}>
              {(data?.forecast.breakdown ?? [])
                .filter((b) => b.stage !== 'CLOSED_LOST')
                .map((b) => (
                  <div
                    key={b.stage}
                    style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, padding: '4px 0' }}
                  >
                    <span>
                      {STAGE_LABELS[b.stage as OpportunityStage] ?? b.stage}（{Math.round(b.probability * 100)}%）
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
                业绩达成（{data?.performance.month ?? currentMonth}）
                {isAdmin && (
                  <Button size="small" style={{ marginLeft: 12 }} onClick={() => {
                    form.setFieldsValue({ targetAmount: (data?.performance.targetAmount ?? 0) / 100 })
                    setTargetOpen(true)
                  }}>
                    设置目标
                  </Button>
                )}
              </span>
            }
            style={{ borderRadius: 10, marginBottom: 16 }}
            bodyStyle={{ padding: 20 }}
          >
            {data?.performance.configured ? (
              <>
                <Statistic
                  title="达成率"
                  value={pct(data.performance.achievementRate)}
                  valueStyle={{
                    color:
                      (data.performance.achievementRate ?? 0) >= 1
                        ? '#3f8600'
                        : (data.performance.achievementRate ?? 0) >= 0.5
                          ? '#fa8c16'
                          : '#cf1322',
                  }}
                />
                <div style={{ marginTop: 12 }}>
                  <Progress
                    percent={Math.round((data.performance.achievementRate ?? 0) * 100)}
                    status={(data.performance.achievementRate ?? 0) >= 1 ? 'success' : 'active'}
                  />
                </div>
                <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
                  已赢单 {formatAmount(data.performance.wonAmount)} 元 / 目标{' '}
                  {formatAmount(data.performance.targetAmount)} 元
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
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card
            loading={isLoading}
            title="客户分析"
            style={{ borderRadius: 10, marginBottom: 16 }}
            bodyStyle={{ padding: 20 }}
          >
            <Row gutter={16}>
              <Col span={8}>
                <Statistic title="客户总数" value={data?.summary.customerCount ?? 0} />
              </Col>
              <Col span={8}>
                <Statistic title="活跃客户" value={data?.summary.activeCustomerCount ?? 0} />
              </Col>
              <Col span={8}>
                <Statistic
                  title="本月新增"
                  value={data?.summary.newCustomersThisMonth ?? 0}
                  valueStyle={{ color: '#3f8600' }}
                />
              </Col>
            </Row>
          </Card>

          <Card
            loading={isLoading}
            title="跟进活动"
            style={{ borderRadius: 10, marginBottom: 16 }}
            bodyStyle={{ padding: 20 }}
          >
            <Statistic title="跟进总数" value={data?.followUps.total ?? 0} style={{ marginBottom: 16 }} />
            {(data?.followUps.byMethod ?? []).map((m) => (
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
            {!data?.followUps.byMethod.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无跟进记录" />}
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card
            loading={isLoading}
            title="最近跟进"
            bodyStyle={{ padding: 0 }}
            style={{ borderRadius: 10, marginBottom: 16 }}
          >
            <Table<{ id: number; method: string; content: string; customerName?: string; followUpBy?: string; createdAt: string }>
              rowKey="id"
              size="small"
              dataSource={data?.followUps.recent ?? []}
              pagination={false}
              locale={{ emptyText: '暂无跟进记录' }}
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
                    width: 80,
                    render: (v?: string) => v ?? '-',
                  },
                  {
                    title: '时间',
                    dataIndex: 'createdAt',
                    width: 150,
                    render: (v: string) => v.replace('T', ' ').slice(0, 16),
                  },
                ] as never
              }
            />
          </Card>
        </Col>
      </Row>

      <Card
        loading={isLoading}
        title="停滞商机预警（超过 7 天未更新）"
        bodyStyle={{ padding: 0 }}
        style={{ borderRadius: 10 }}
      >
        <Table<StalledOpportunity>
          rowKey="id"
          size="middle"
          dataSource={data?.stalledOpportunities ?? []}
          columns={stalledColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无停滞商机，管道健康' }}
        />
      </Card>

      <Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12, marginBottom: 0 }}>
        生成时间：{data?.generatedAt ? data.generatedAt.replace('T', ' ').slice(0, 19) : '-'}
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
