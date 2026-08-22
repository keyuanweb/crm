import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, Result, Row, Statistic, Table, Tag, Typography } from 'antd'
import {
  FundOutlined,
  ReloadOutlined,
  RiseOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { fetchPipelineStats } from '../../services/statsService'
import { STAGE_LABELS, formatAmount } from '../../types/opportunity'
import type { StageStat } from '../../types/stats'

const { Title, Paragraph } = Typography

const stageColor: Record<string, string> = {
  INITIAL_CONTACT: 'blue',
  NEGOTIATING: 'gold',
  CLOSED_WON: 'green',
  CLOSED_LOST: 'red',
}

export default function OpportunityPipelinePage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['pipeline-stats'],
    queryFn: fetchPipelineStats,
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

  const columns = [
    {
      title: '阶段',
      dataIndex: 'stage',
      render: (stage: string) => (
        <Tag color={stageColor[stage] ?? 'default'}>
          {STAGE_LABELS[stage as keyof typeof STAGE_LABELS] ?? stage}
        </Tag>
      ),
    },
    { title: '商机数量', dataIndex: 'count' },
    {
      title: '金额合计（元）',
      dataIndex: 'amountTotal',
      render: (v: number) => formatAmount(v),
    },
  ]

  const statCards = [
    {
      title: '商机总数',
      value: data?.grandTotal.count ?? 0,
      icon: <TeamOutlined />,
      bg: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)',
    },
    {
      title: '金额合计',
      value: formatAmount(data?.grandTotal.amountTotal),
      prefix: '¥',
      icon: <FundOutlined />,
      bg: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)',
    },
    {
      title: '阶段数',
      value: data?.stages.length ?? 0,
      icon: <RiseOutlined />,
      bg: 'linear-gradient(135deg, #fa8c16 0%, #ffc069 100%)',
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          商机管道统计
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          全量商机按阶段汇总，实时反映销售管道健康度
        </Paragraph>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        {statCards.map((card) => (
          <Col xs={24} sm={12} md={8} key={card.title}>
            <Card
              loading={isLoading}
              bodyStyle={{ padding: 20 }}
              style={{
                background: card.bg,
                border: 'none',
                borderRadius: 10,
                color: '#fff',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.85)', marginBottom: 8 }}>
                    {card.title}
                  </div>
                  <Statistic
                    value={card.value}
                    prefix={card.prefix}
                    valueStyle={{ color: '#fff', fontSize: 26, fontWeight: 600 }}
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

      <Card
        loading={isLoading}
        title="阶段明细"
        bodyStyle={{ padding: 0 }}
        style={{ borderRadius: 10 }}
      >
        <Table<StageStat>
          rowKey="stage"
          size="middle"
          loading={isLoading}
          dataSource={data?.stages ?? []}
          columns={columns as never}
          pagination={false}
        />
        <div style={{ padding: '12px 24px', borderTop: '1px solid #f0f0f0' }}>
          <Paragraph type="secondary" style={{ margin: 0, fontSize: 12 }}>
            生成时间：
            {data?.generatedAt ? data.generatedAt.replace('T', ' ').slice(0, 19) : '-'}
          </Paragraph>
        </div>
      </Card>
    </div>
  )
}
