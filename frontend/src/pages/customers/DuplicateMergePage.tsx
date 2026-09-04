import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  App,
  Alert,
  Button,
  Card,
  Empty,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ScanOutlined } from '@ant-design/icons'
import { fetchDuplicates, mergeCustomers } from '../../services/customerMergeService'
import { extractErrorMessage } from '../../services/apiClient'
import type { DuplicateGroup, DuplicateItem } from '../../types/merge'

const SIM_COLORS: Record<number, string> = {
  100: 'red',
  90: 'volcano',
  85: 'orange',
}

export default function DuplicateMergePage() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [groups, setGroups] = useState<DuplicateGroup[]>([])
  const [scanning, setScanning] = useState(false)
  const [scanned, setScanned] = useState(false)
  const [merging, setMerging] = useState(false)

  const onScan = async () => {
    setScanning(true)
    try {
      const result = await fetchDuplicates()
      setGroups(result)
      setScanned(true)
      message.success(t('pages.duplicateMerge.scanSuccess', { count: result.length }))
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.duplicateMerge.scanFailed')))
    } finally {
      setScanning(false)
    }
  }

  const onMerge = async (group: DuplicateGroup, item: DuplicateItem) => {
    setMerging(true)
    try {
      const r = await mergeCustomers(group.primaryId, item.customerId)
      message.success(
        t('pages.duplicateMerge.mergeSuccess', {
          orders: r.movedOrders,
          opportunities: r.movedOpportunities,
          contacts: r.movedContacts,
          followUps: r.movedFollowUps,
          tickets: r.movedTickets,
        }),
      )
      // 重新扫描
      const result = await fetchDuplicates()
      setGroups(result)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.duplicateMerge.msgMergeFailed')))
    } finally {
      setMerging(false)
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card style={{ borderRadius: 10 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Button type="primary" icon={<ScanOutlined />} loading={scanning} onClick={() => void onScan()}>
              {t('pages.duplicateMerge.btnScan')}
            </Button>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              {t('pages.duplicateMerge.scanHint')}
            </Typography.Text>
          </Space>
          <Alert
            type="info"
            showIcon
            message={t('pages.duplicateMerge.mergeRule')}
          />
        </Space>
      </Card>

      {scanned && !scanning && groups.length === 0 ? (
        <Card style={{ borderRadius: 10 }}>
          <Empty description={t('pages.duplicateMerge.noDuplicates')} />
        </Card>
      ) : null}

      <Spin spinning={scanning || merging}>
        {groups.map((g) => (
          <Card
            key={g.id}
            style={{ borderRadius: 10, marginBottom: 12 }}
            title={
              <Space>
                <Typography.Text strong>{g.primaryName}</Typography.Text>
                <Tag color="blue">{t('pages.duplicateMerge.primaryLabel')} #{g.primaryId}</Tag>
              </Space>
            }
            extra={t('pages.duplicateMerge.duplicateCount', { count: g.duplicates.length })}
          >
            <Table<DuplicateItem>
              size="small"
              rowKey="customerId"
              dataSource={g.duplicates}
              pagination={false}
              columns={[
                { title: t('pages.duplicateMerge.colDuplicateCustomer'), dataIndex: 'name' },
                { title: t('pages.duplicateMerge.colCompany'), dataIndex: 'company', render: (v?: string) => v || '-' },
                {
                  title: t('pages.duplicateMerge.colScore'),
                  dataIndex: 'similarity',
                  width: 90,
                  render: (v: number) => <Tag color={SIM_COLORS[v] ?? 'default'}>{v}%</Tag>,
                },
                {
                  title: t('pages.duplicateMerge.colRelatedData'),
                  dataIndex: 'relatedCount',
                  width: 90,
                  render: (v: number) => <Tag color={v > 0 ? 'orange' : 'default'}>{v} {t('pages.duplicateMerge.relatedCountSuffix')}</Tag>,
                },
                {
                  title: t('pages.duplicateMerge.colAction'),
                  width: 90,
                  render: (_, item) => (
                    <Popconfirm
                      title={t('pages.duplicateMerge.mergeConfirmTitle', { name: item.name, primary: g.primaryName })}
                      description={t('pages.duplicateMerge.mergeConfirmDesc')}
                      onConfirm={() => void onMerge(g, item)}
                    >
                      <Button size="small" type="primary" danger loading={merging}>
                        {t('pages.duplicateMerge.btnMerge')}
                      </Button>
                    </Popconfirm>
                  ),
                },
              ]}
            />
          </Card>
        ))}
      </Spin>
    </Space>
  )
}
