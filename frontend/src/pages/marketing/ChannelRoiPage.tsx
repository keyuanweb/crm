import { useEffect, useState } from 'react'
import { ProCard, Statistic } from '@ant-design/pro-components'
import { App, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fetchChannelRoi } from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import { CAMPAIGN_CHANNEL_LABELS, type ChannelRoi } from '../../types/marketing'

export default function ChannelRoiPage() {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<ChannelRoi[]>([])

  const load = async () => {
    setLoading(true)
    try {
      setRows(await fetchChannelRoi())
    } catch (err) {
      message.error(extractErrorMessage(err, '加载失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const totalCost = rows.reduce((sum, r) => sum + r.totalCost, 0)
  const totalRevenue = rows.reduce((sum, r) => sum + r.estimatedRevenue, 0)
  const totalLeads = rows.reduce((sum, r) => sum + r.leadCount, 0)
  const totalCustomers = rows.reduce((sum, r) => sum + r.customerCount, 0)

  const columns: ColumnsType<ChannelRoi> = [
    {
      title: '渠道',
      dataIndex: 'channel',
      render: (_, row) => <Tag color="blue">{CAMPAIGN_CHANNEL_LABELS[row.channel] ?? row.channel}</Tag>,
    },
    { title: '活动数', dataIndex: 'campaignCount' },
    { title: '总成本', dataIndex: 'totalCost' },
    { title: '归因线索', dataIndex: 'leadCount' },
    { title: '归因客户', dataIndex: 'customerCount' },
    {
      title: '转化率',
      dataIndex: 'conversionRate',
      render: (_, row) =>
        row.conversionRate == null ? '-' : `${(row.conversionRate * 100).toFixed(1)}%`,
    },
    { title: '预估收益', dataIndex: 'estimatedRevenue' },
    {
      title: 'ROI',
      dataIndex: 'roi',
      render: (_, row) =>
        row.roi == null ? (
          <Tag>无成本</Tag>
        ) : (
          <Tag color={row.roi >= 1 ? 'green' : row.roi >= 0 ? 'gold' : 'red'}>
            {(row.roi * 100).toFixed(1)}%
          </Tag>
        ),
    },
  ]

  return (
    <>
      <ProCard
        title="渠道 ROI 统计"
        loading={loading}
        extra={<a onClick={() => void load()}>刷新</a>}
        style={{ marginBottom: 16 }}
      >
        <div style={{ display: 'flex', gap: 24 }}>
          <Statistic title="渠道数" value={rows.length} />
          <Statistic title="总成本" value={totalCost} />
          <Statistic title="归因线索" value={totalLeads} />
          <Statistic title="归因客户" value={totalCustomers} />
          <Statistic title="预估收益" value={totalRevenue} />
        </div>
      </ProCard>
      <Table<ChannelRoi>
        size="small"
        rowKey="channel"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
        locale={{ emptyText: '暂无活动数据，创建营销活动后自动归因统计' }}
      />
    </>
  )
}
