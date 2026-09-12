import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProCard, Statistic } from '@ant-design/pro-components'
import { App, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { fetchChannelRoi } from '../../services/marketingService'
import { extractErrorMessage } from '../../services/apiClient'
import type { ChannelRoi } from '../../types/marketing'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

export default function ChannelRoiPage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [rows, setRows] = useState<ChannelRoi[]>([])

  const load = async () => {
    setLoading(true)
    try {
      setRows(await fetchChannelRoi())
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.channelRoi.msgLoadFailed')))
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
      title: t('pages.marketing.channelRoi.colChannel'),
      dataIndex: 'channel',
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.campaignChannel, row.channel)}</Tag>,
    },
    { title: t('pages.marketing.channelRoi.colCampaignCount'), dataIndex: 'campaignCount' },
    { title: t('pages.marketing.channelRoi.colTotalCost'), dataIndex: 'totalCost' },
    { title: t('pages.marketing.channelRoi.colLeadCount'), dataIndex: 'leadCount' },
    { title: t('pages.marketing.channelRoi.colCustomerCount'), dataIndex: 'customerCount' },
    {
      title: t('pages.marketing.channelRoi.colConversionRate'),
      dataIndex: 'conversionRate',
      render: (_, row) =>
        row.conversionRate == null ? '-' : `${(row.conversionRate * 100).toFixed(1)}%`,
    },
    { title: t('pages.marketing.channelRoi.colEstimatedRevenue'), dataIndex: 'estimatedRevenue' },
    {
      title: 'ROI',
      dataIndex: 'roi',
      render: (_, row) =>
        row.roi == null ? (
          <Tag>{t('pages.marketing.channelRoi.noCost')}</Tag>
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
        title={t('pages.marketing.channelRoi.title')}
        loading={loading}
        extra={<a onClick={() => void load()}>{t('pages.marketing.channelRoi.btnRefresh')}</a>}
        style={{ marginBottom: 16 }}
      >
        <div style={{ display: 'flex', gap: 24 }}>
          <Statistic title={t('pages.marketing.channelRoi.statChannelCount')} value={rows.length} />
          <Statistic title={t('pages.marketing.channelRoi.statTotalCost')} value={totalCost} />
          <Statistic title={t('pages.marketing.channelRoi.statLeadCount')} value={totalLeads} />
          <Statistic title={t('pages.marketing.channelRoi.statCustomerCount')} value={totalCustomers} />
          <Statistic title={t('pages.marketing.channelRoi.statEstimatedRevenue')} value={totalRevenue} />
        </div>
      </ProCard>
      <Table<ChannelRoi>
        size="small"
        rowKey="channel"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={false}
        locale={{ emptyText: t('pages.marketing.channelRoi.emptyText') }}
      />
    </>
  )
}
