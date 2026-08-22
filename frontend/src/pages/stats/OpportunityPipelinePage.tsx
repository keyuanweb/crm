import { useQuery } from '@tanstack/react-query'
import { Button, Card, Result, Statistic, Table, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { fetchPipelineStats } from '../../services/statsService'
import { STAGE_LABELS, formatAmount } from '../../types/opportunity'
import type { StageStat } from '../../types/stats'

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
        <Tag color={stageColor[stage] ?? 'default'}>{STAGE_LABELS[stage as keyof typeof STAGE_LABELS] ?? stage}</Tag>
      ),
    },
    { title: '商机数量', dataIndex: 'count' },
    { title: '金额合计（元）', dataIndex: 'amountTotal', render: (v: number) => formatAmount(v) },
  ]

  return (
    <div>
      <Typography.Title level={4}>商机管道统计</Typography.Title>
      <Card loading={isLoading}>
        <div style={{ display: 'flex', gap: 32, marginBottom: 24 }}>
          <Statistic title="商机总数" value={data?.grandTotal.count ?? 0} />
          <Statistic
            title="金额合计（元）"
            value={formatAmount(data?.grandTotal.amountTotal)}
          />
        </div>
        <Table<StageStat>
          rowKey="stage"
          size="small"
          loading={isLoading}
          dataSource={data?.stages ?? []}
          columns={columns as never}
          pagination={false}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12 }}>
          生成时间：
          {data?.generatedAt ? data.generatedAt.replace('T', ' ').slice(0, 19) : '-'}
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
