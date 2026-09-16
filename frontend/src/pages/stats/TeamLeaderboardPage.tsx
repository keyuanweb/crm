import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Progress, Radio, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useTranslation } from 'react-i18next'
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

/**
 * 达成率文案。**`t` 必传**（098）：原签名是 `t?: (key) => string`，缺省时返回**裸中文 `'未设目标'`**
 * ——那是模块级辅助函数拿不到 `useTranslation` 的 hook 时的兜底。但 `pages.teamLeaderboard.noTarget`
 * 这个键**本来就已经存在**（同文件 L49 的表格 render 一直在用它），故兜底中文是纯冗余：
 * 它只会在调用方忘传 `t` 时把界面变成中文。改为必传后，**唯一调用点 L79 `rateLabel(v, t)` 行为不变**。
 */
const rateLabel = (rate: number | undefined, t: (key: string) => string) => {
  if (rate === undefined || rate === null) return t('pages.teamLeaderboard.noTarget')
  return `${Math.round(rate * 100)}%`
}

export default function TeamLeaderboardPage() {
  const { t } = useTranslation()
  const [month, setMonth] = useState<Dayjs>(dayjs())
  const [sortBy, setSortBy] = useState<'rate' | 'amount'>('rate')

  const { data, isLoading } = useQuery({
    queryKey: ['leaderboard', month.format('YYYY-MM'), sortBy],
    queryFn: () => fetchLeaderboard(month.format('YYYY-MM'), sortBy),
  })

  const columns = [
    {
      title: t('pages.teamLeaderboard.colRank'),
      key: 'rank',
      width: 70,
      render: (_: unknown, __: LeaderboardItem, index: number) => {
        const medals = ['🥇', '🥈', '🥉']
        return <span style={{ fontWeight: 600 }}>{medals[index] ?? index + 1}</span>
      },
    },
    { title: t('pages.teamLeaderboard.colUser'), dataIndex: 'displayName', width: 160 },
    {
      title: t('pages.teamLeaderboard.colTargetAmount'),
      dataIndex: 'targetAmount',
      width: 160,
      render: (v?: number) => (v === undefined || v === null ? <Tag>{t('pages.teamLeaderboard.noTarget')}</Tag> : formatAmount(v)),
    },
    {
      title: t('pages.teamLeaderboard.colWonAmount'),
      dataIndex: 'wonAmount',
      width: 160,
      render: (v: number) => <span style={{ fontWeight: 500 }}>{formatAmount(v)}</span>,
    },
    {
      title: t('pages.teamLeaderboard.colAchievementRate'),
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
            {rateLabel(v, t)}
          </Tag>
        </div>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          {t('pages.teamLeaderboard.title')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.teamLeaderboard.description')}
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
          <Radio.Button value="rate">{t('pages.teamLeaderboard.sortByRate')}</Radio.Button>
          <Radio.Button value="amount">{t('pages.teamLeaderboard.sortByAmount')}</Radio.Button>
        </Radio.Group>
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table<LeaderboardItem>
          rowKey="userId"
          size="small"
          loading={isLoading}
          dataSource={data?.items ?? []}
          columns={columns as never}
          pagination={false}
          locale={{ emptyText: t('pages.teamLeaderboard.emptyText') }}
        />
      </Card>
    </div>
  )
}
