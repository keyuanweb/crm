import { useState } from 'react'
import { Button, Card, DatePicker, Progress, Radio, Select, Table, Tag, Typography, message } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { exportReport, queryReport } from '../../services/reportService'
import { formatAmount } from '../../types/opportunity'
import type { ReportDimension, ReportMetric, ReportQuery, ReportResult, ReportRow } from '../../types/report'

const { Title, Paragraph } = Typography

const DIMENSION_LABELS: Record<string, string> = {
  SALES: '销售',
  PRODUCT: '产品',
  SOURCE: '线索来源',
  STAGE: '商机阶段',
  TIME: '时间',
}

const STAGE_LABELS: Record<string, string> = {
  INITIAL_CONTACT: '初步接触',
  NEGOTIATING: '谈判中',
  CLOSED_WON: '已赢单',
  CLOSED_LOST: '已输单',
}

export default function ReportCenterPage() {
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
      message.error('报表查询失败，请检查参数')
    } finally {
      setLoading(false)
    }
  }

  const onExport = async () => {
    try {
      await exportReport(buildQuery())
      message.success('报表已导出')
    } catch {
      message.error('导出失败')
    }
  }

  const columns = [
    {
      title: DIMENSION_LABELS[dimension] ?? '维度',
      dataIndex: 'dimensionValue',
      render: (v: string) =>
        dimension === 'STAGE' ? <Tag>{STAGE_LABELS[v] ?? v}</Tag> : v,
    },
    { title: '数量', dataIndex: 'count', width: 120 },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      width: 160,
      render: (v: number) => (metric === 'AMOUNT' ? formatAmount(v) : '-'),
    },
    {
      title: '占比',
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
          自定义报表
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          按维度（销售/产品/来源/阶段/时间）与指标（数量/金额）聚合业务数据，支持导出 Excel。
        </Paragraph>
      </div>

      <Card style={{ borderRadius: 10, marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>维度</div>
            <Select
              value={dimension}
              onChange={(v) => setDimension(v)}
              style={{ width: 140 }}
              options={Object.entries(DIMENSION_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </div>
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>指标</div>
            <Radio.Group value={metric} onChange={(e) => setMetric(e.target.value)}>
              <Radio.Button value="COUNT">数量</Radio.Button>
              <Radio.Button value="AMOUNT">金额</Radio.Button>
            </Radio.Group>
          </div>
          {dimension === 'TIME' && (
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>时间粒度</div>
              <Radio.Group value={granularity} onChange={(e) => setGranularity(e.target.value)}>
                <Radio.Button value="DAY">按日</Radio.Button>
                <Radio.Button value="MONTH">按月</Radio.Button>
              </Radio.Group>
            </div>
          )}
          <div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>时间范围</div>
            <DatePicker.RangePicker
              value={range}
              onChange={(v) => setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)}
            />
          </div>
          {dimension !== 'SOURCE' && dimension !== 'TIME' && (
            <div>
              <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>阶段过滤（可选）</div>
              <Select
                allowClear
                placeholder="全部阶段"
                value={stageFilter}
                onChange={(v) => setStageFilter(v)}
                style={{ width: 140 }}
                options={Object.entries(STAGE_LABELS).map(([value, label]) => ({ value, label }))}
              />
            </div>
          )}
          <div style={{ alignSelf: 'flex-end', display: 'flex', gap: 8 }}>
            <Button type="primary" loading={loading} onClick={() => void onQuery()}>
              查询
            </Button>
            <Button icon={<DownloadOutlined />} disabled={!result} onClick={() => void onExport()}>
              导出
            </Button>
          </div>
        </div>
      </Card>

      <Card style={{ borderRadius: 10 }} styles={{ body: { padding: 0 } }}>
        <Table<ReportRow>
          rowKey="dimensionValue"
          size="small"
          loading={loading}
          dataSource={result?.rows ?? []}
          columns={columns as never}
          pagination={false}
          locale={{ emptyText: '点击查询生成报表' }}
          footer={
            result
              ? () => (
                  <div style={{ display: 'flex', gap: 32 }}>
                    <span>
                      合计数量：<b>{result.totalCount}</b>
                    </span>
                    {metric === 'AMOUNT' && (
                      <span>
                        合计金额：<b>{formatAmount(result.totalAmount)}</b> 元
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
