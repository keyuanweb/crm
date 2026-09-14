import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Card, DatePicker, Progress, Radio, Select, Table, Tag, Typography, message } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { exportReport, queryReport } from '../../services/reportService'
import { formatAmount } from '../../types/opportunity'
import type { ReportDimension, ReportMetric, ReportQuery, ReportResult, ReportRow } from '../../types/report'

const { Title, Paragraph } = Typography

const DIMENSION_KEYS: Record<ReportDimension, string> = {
  SALES: 'pages.reportCenter.dimSales',
  PRODUCT: 'pages.reportCenter.dimProduct',
  SOURCE: 'pages.reportCenter.dimSource',
  STAGE: 'pages.reportCenter.dimStage',
  TIME: 'pages.reportCenter.dimTime',
}

const STAGE_KEYS: Record<string, string> = {
  INITIAL_CONTACT: 'pages.reportCenter.stageInitialContact',
  NEGOTIATING: 'pages.reportCenter.stageNegotiating',
  CLOSED_WON: 'pages.reportCenter.stageClosedWon',
  CLOSED_LOST: 'pages.reportCenter.stageClosedLost',
}

export default function ReportCenterPage() {
  const { t } = useTranslation()
  const [dimension, setDimension] = useState<ReportDimension>('SALES')
  const [metric, setMetric] = useState<ReportMetric>('AMOUNT')
  const [granularity, setGranularity] = useState<'DAY' | 'MONTH'>('MONTH')
  const [stageFilter, setStageFilter] = useState<string | undefined>()
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>([
    dayjs().startOf('month'),
    dayjs().endOf('month'),
  ])
  const [result, setResult] = useState<ReportResult | null>(null)
  const [loading, setLoading] = useState(false)

  const buildQuery = (): ReportQuery => ({
    dimension,
    metric,
    granularity: dimension === 'TIME' ? granularity : undefined,
    startDate: range?.[0]?.format('YYYY-MM-DD'),
    endDate: range?.[1]?.format('YYYY-MM-DD'),
    stageFilter,
  })

  const onQuery = async () => {
    setLoading(true)
    try {
      const res = await queryReport(buildQuery())
      setResult(res)
    } catch {
      message.error(t('pages.reportCenter.msgQueryFailed'))
    } finally {
      setLoading(false)
    }
  }

  const onExport = async () => {
    try {
      await exportReport(buildQuery())
      message.success(t('pages.reportCenter.msgExported'))
    } catch {
      message.error(t('pages.reportCenter.msgExportFailed'))
    }
  }

  const columns = [
    {
      title: t(DIMENSION_KEYS[dimension]) ?? t('pages.reportCenter.dimLabel'),
      dataIndex: 'dimensionValue',
      render: (v: string) =>
        dimension === 'STAGE' ? <Tag>{t(STAGE_KEYS[v]) ?? v}</Tag> : v,
    },
    { title: t('pages.reportCenter.colCount'), dataIndex: 'count', width: 120 },
    {
      title: t('pages.reportCenter.colAmount'),
      dataIndex: 'amount',
      width: 160,
      render: (v: number) => (metric === 'AMOUNT' ? formatAmount(v) : '-'),
    },
    {
      title: t('pages.reportCenter.colRatio'),
      dataIndex: 'ratio',
      width: 200,
      render: (v: number) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Progress percent={Math.round(v * 100)} showInfo={false} size="small" style={{ flex: 1, maxWidth: 120 }} />
          <span style={{ fontSize: 12 }}>{Math.round(v * 100)}%</span>
        </div>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          {t('pages.reportCenter.title')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.reportCenter.description')}
        </Paragraph>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>{t('pages.reportCenter.dimLabel')}</div>
            <Select
              value={dimension}
              onChange={(v) => setDimension(v)}
              style={{ width: 140 }}
              options={Object.entries(DIMENSION_KEYS).map(([value, key]) => ({ value, label: t(key) }))}
            />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>{t('pages.reportCenter.metricLabel')}</div>
            <Radio.Group value={metric} onChange={(e) => setMetric(e.target.value)}>
              <Radio.Button value="COUNT">{t('pages.reportCenter.colCount')}</Radio.Button>
              <Radio.Button value="AMOUNT">{t('pages.reportCenter.colAmount')}</Radio.Button>
            </Radio.Group>
          </div>
          {dimension === 'TIME' && (
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>{t('pages.reportCenter.timeGranularityLabel')}</div>
              <Radio.Group value={granularity} onChange={(e) => setGranularity(e.target.value)}>
                <Radio.Button value="DAY">{t('pages.reportCenter.granularityDay')}</Radio.Button>
                <Radio.Button value="MONTH">{t('pages.reportCenter.granularityMonth')}</Radio.Button>
              </Radio.Group>
            </div>
          )}
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>{t('pages.reportCenter.timeRangeLabel')}</div>
            <DatePicker.RangePicker
              value={range}
              onChange={(v) => setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)}
            />
          </div>
          {dimension !== 'SOURCE' && dimension !== 'TIME' && (
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>{t('pages.reportCenter.stageFilterLabel')}</div>
              <Select
                allowClear
                placeholder={t('pages.reportCenter.placeholderStage')}
                value={stageFilter}
                onChange={(v) => setStageFilter(v)}
                style={{ width: 140 }}
                options={Object.entries(STAGE_KEYS).map(([value, key]) => ({ value, label: t(key) }))}
              />
            </div>
          )}
          <div style={{ alignSelf: 'flex-end', display: 'flex', gap: 8 }}>
            <Button type="primary" loading={loading} onClick={() => void onQuery()}>
              {t('pages.reportCenter.btnQuery')}
            </Button>
            <Button icon={<DownloadOutlined />} disabled={!result} onClick={() => void onExport()}>
              {t('pages.reportCenter.btnExport')}
            </Button>
          </div>
        </div>
      </Card>

      <Card styles={{ body: { padding: 0 } }}>
        <Table<ReportRow>
          rowKey="dimensionValue"
          size="small"
          loading={loading}
          dataSource={result?.rows ?? []}
          columns={columns as never}
          pagination={false}
          locale={{ emptyText: t('pages.reportCenter.emptyText') }}
          footer={
            result
              ? () => (
                  <div style={{ display: 'flex', gap: 32 }}>
                    <span>
                      {t('pages.reportCenter.totalCount')}<b>{result.totalCount}</b>
                    </span>
                    {metric === 'AMOUNT' && (
                      <span>
                        {t('pages.reportCenter.totalAmount')}<b>{formatAmount(result.totalAmount)}</b> {t('pages.reportCenter.unit')}
                      </span>
                    )}
                  </div>
                )
              : undefined
          }
        />
      </Card>
    </div>
  )
}
