import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, Col, DatePicker, Row, Statistic, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { fetchSurveyStats } from '../../services/ticketSurveyService'
import type { Dayjs } from 'dayjs'

/** 满意度统计页（051）：CSAT 均值 + NPS 分布。 */
export default function SatisfactionStatsPage() {
  const { t } = useTranslation()
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
        ? t('pages.survey.npsExcellent')
        : stats.npsScore >= 0
          ? t('pages.survey.npsGood')
          : t('pages.survey.npsNeedsImprovement')
      : '-'

  return (
    <div>
      <Card
        title={t('pages.survey.title')}
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
          <Typography.Text type="danger">{t('pages.survey.msgLoadFailed')}</Typography.Text>
        ) : (
          <Row gutter={[24, 24]}>
            <Col xs={12} md={6}>
              <Statistic title={t('pages.survey.statSampleCount')} value={stats.sampleCount} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title={t('pages.survey.statCsatAvg')}
                value={stats.csatAverage}
                precision={1}
                suffix="/ 5"
              />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title={t('pages.survey.statNps')}
                value={stats.npsScore}
                suffix={<Tag color={stats.npsScore >= 0 ? 'green' : 'red'}>{npsLabel}</Tag>}
              />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title={t('pages.survey.statSampleCoverage')}
                value={stats.sampleCount > 0 ? t('pages.survey.coverageValid') : t('pages.survey.coverageNone')}
              />
            </Col>
            <Col span={24}>
              <Typography.Title level={5} style={{ marginTop: 8 }}>
                {t('pages.survey.npsDistribution')}
              </Typography.Title>
              <Row gutter={16}>
                <Col xs={8}>
                  <Statistic
                    title={t('pages.survey.promoter')}
                    value={stats.promoter.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#3f8600' }}
                  />
                  <Typography.Text type="secondary">
                    {t('pages.survey.countUnit', { count: stats.promoter.count })}
                  </Typography.Text>
                </Col>
                <Col xs={8}>
                  <Statistic
                    title={t('pages.survey.passive')}
                    value={stats.passive.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#faad14' }}
                  />
                  <Typography.Text type="secondary">
                    {t('pages.survey.countUnit', { count: stats.passive.count })}
                  </Typography.Text>
                </Col>
                <Col xs={8}>
                  <Statistic
                    title={t('pages.survey.detractor')}
                    value={stats.detractor.percent}
                    precision={1}
                    suffix="%"
                    valueStyle={{ color: '#cf1322' }}
                  />
                  <Typography.Text type="secondary">
                    {t('pages.survey.countUnit', { count: stats.detractor.count })}
                  </Typography.Text>
                </Col>
              </Row>
            </Col>
          </Row>
        )}
      </Card>
    </div>
  )
}
