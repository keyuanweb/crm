import { useState } from 'react'
import { Card, Col, DatePicker, Row, Statistic, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { fetchSurveyStats } from '../../services/ticketSurveyService'
import type { Dayjs } from 'dayjs'

/** 满意度统计页（051）：CSAT 均值 + NPS 分布。 */
export default function SatisfactionStatsPage() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['survey-stats', range],
    queryFn: () =>
      fetchSurveyStats(
        range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
        range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
      ),
  })

  const stats = data
  const npsLabel =
    stats && stats.sampleCount > 0
      ? stats.npsScore >= 50
        ? '优秀'
        : stats.npsScore >= 0
          ? '良好'
          : '需改进'
      : '-'

  return (
    <div>
      <Card
        title="工单满意度（CSAT / NPS）"
        style={{ borderRadius: 10 }}
        extra={
          <DatePicker.RangePicker
            allowClear
            onChange={(v) => setRange(v as [Dayjs | null, Dayjs | null] | null)}
          />
        }
      >
        {isLoading ? (
          <Card loading />
        ) : error || !stats ? (
          <Typography.Text type="danger">加载失败</Typography.Text>
        ) : (
          <Row gutter={[24, 24]}>
            <Col xs={12} md={6}>
              <Statistic title="样本数" value={stats.sampleCount} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="CSAT 平均分（1-5）"
                value={stats.csatAverage}
                precision={1}
                suffix="/ 5"
              />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="NPS 得分"
                value={stats.npsScore}
                suffix={<Tag color={stats.npsScore >= 0 ? 'green' : 'red'}>{npsLabel}</Tag>}
              />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title="样本覆盖" value={stats.sampleCount > 0 ? '有效' : '暂无'} />
            </Col>
            <Col span={24}>
              <Typography.Title level={5} style={{ marginTop: 8 }}>
                NPS 分布
              </Typography.Title>
              <Row gutter={16}>
                <Col xs={8}>
                  <Statistic
                    title="推荐者（5 分）"
                    value={stats.promoter.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#3f8600' }}
                  />
                  <Typography.Text type="secondary">
                    {stats.promoter.count} 条
                  </Typography.Text>
                </Col>
                <Col xs={8}>
                  <Statistic
                    title="中立者（4 分）"
                    value={stats.passive.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#faad14' }}
                  />
                  <Typography.Text type="secondary">{stats.passive.count} 条</Typography.Text>
                </Col>
                <Col xs={8}>
                  <Statistic
                    title="贬损者（1-3 分）"
                    value={stats.detractor.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#cf1322' }}
                  />
                  <Typography.Text type="secondary">{stats.detractor.count} 条</Typography.Text>
                </Col>
              </Row>
            </Col>
          </Row>
        )}
      </Card>
    </div>
  )
}
