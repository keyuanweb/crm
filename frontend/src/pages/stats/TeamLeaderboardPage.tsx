import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Progress, Radio, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { fetchLeaderboard } from '../../services/statsService'
import { formatAmount } from '../../types/opportunity'
import type { LeaderboardItem } from '../../types/stats'

const { Title, Paragraph } = Typography

const rateColor = (rate?: number) => {
  if (rate === undefined || rate === null) return 'default'
  if (rate >= 0.8) return 'green'
  if (rate >= 0.5) return 'gold'
  return 'red'
}

const rateLabel = (rate?: number) => {
  if (rate === undefined || rate === null) return '未设目标'
  return `${Math.round(rate * 100)}%`
}

export default function TeamLeaderboardPage() {
  const [month, setMonth] = useState<Dayjs>(dayjs())
  const [sortBy, setSortBy] = useState<'rate' | 'amount'>('rate')

  const { data, isLoading } = useQuery({
    queryKey: ['leaderboard', month.format('YYYY-MM'), sortBy],
    queryFn: () => fetchLeaderboard(month.format('YYYY-MM'), sortBy),
  })

  const columns = [
    {
      title: '排名',
      key: 'rank',
      width: 70,
      render: (_: unknown, __: LeaderboardItem, index: number) => {
        const medals = ['🥇', '🥈', '🥉']
        return <span style={{ fontWeight: 600 }}>{medals[index] ?? index + 1}</span>
      },
    },
    { title: '销售', dataIndex: 'displayName', width: 160 },
    {
      title: '目标金额（元）',
      dataIndex: 'targetAmount',
      width: 160,
      render: (v?: number) => (v === undefined || v === null ? <Tag>未设目标</Tag> : formatAmount(v)),
    },
    {
      title: '赢单金额（元）',
      dataIndex: 'wonAmount',
      width: 160,
      render: (v: number) => <span style={{ fontWeight: 500 }}>{formatAmount(v)}</span>,
    },
    {
      title: '达成率',
      dataIndex: 'achievementRate',
      width: 240,
      render: (v?: number) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Progress
            percent={v === undefined || v === null ? 0 : Math.round(v * 100)}
            showInfo={false}
            size="small"
            strokeColor={
              v === undefined || v === null
                ? '#d9d9d9'
                : v >= 0.8
                  ? '#52c41a'
                  : v >= 0.5
                    ? '#faad14'
                    : '#ff4d4f'
            }
            style={{ flex: 1, maxWidth: 140 }}
          />
          <Tag color={rateColor(v)} style={{ marginInlineEnd: 0 }}>
            {rateLabel(v)}
          </Tag>
        </div>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          团队销售排行
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          按月度对比各销售的目标、赢单金额与达成率，驱动良性竞争。
        </Paragraph>
      </div>

      <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 16 }}>
        <DatePicker
          picker="month"
          value={month}
          onChange={(m) => m && setMonth(m)}
          allowClear={false}
          style={{ width: 140 }}
        />
        <Radio.Group value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
          <Radio.Button value="rate">按达成率</Radio.Button>
          <Radio.Button value="amount">按赢单金额</Radio.Button>
        </Radio.Group>
      </div>

      <Card style={{ borderRadius: 10 }} styles={{ body: { padding: 0 } }}>
        <Table<LeaderboardItem>
          rowKey="userId"
          size="small"
          loading={isLoading}
          dataSource={data?.items ?? []}
          columns={columns as never}
          pagination={false}
          locale={{ emptyText: '暂无排行数据' }}
        />
      </Card>
    </div>
  )
}
