import { useState } from 'react'
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
      message.success(`扫描完成，发现 ${result.length} 组疑似重复`)
    } catch (err) {
      message.error(extractErrorMessage(err, '扫描失败'))
    } finally {
      setScanning(false)
    }
  }

  const onMerge = async (group: DuplicateGroup, item: DuplicateItem) => {
    setMerging(true)
    try {
      const r = await mergeCustomers(group.primaryId, item.customerId)
      message.success(
        `已合并：转移订单 ${r.movedOrders} / 商机 ${r.movedOpportunities} / 联系人 ${r.movedContacts} / 跟进 ${r.movedFollowUps} / 工单 ${r.movedTickets}`,
      )
      // 重新扫描
      const result = await fetchDuplicates()
      setGroups(result)
    } catch (err) {
      message.error(extractErrorMessage(err, '合并失败'))
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
              扫描疑似重复客户
            </Button>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              按名称（归一化）+ 电话/邮箱精确匹配；扫描全部可见客户
            </Typography.Text>
          </Space>
          <Alert
            type="info"
            showIcon
            message="合并规则：主记录保留（字段优先），从记录关联数据（订单/商机/联系人/跟进/工单）转移至主记录，从记录进入回收站可恢复。"
          />
        </Space>
      </Card>

      {scanned && !scanning && groups.length === 0 ? (
        <Card style={{ borderRadius: 10 }}>
          <Empty description="未发现重复客户 🎉" />
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
                <Tag color="blue">主记录 #{g.primaryId}</Tag>
              </Space>
            }
            extra={`疑似重复 ${g.duplicates.length} 条`}
          >
            <Table<DuplicateItem>
              size="small"
              rowKey="customerId"
              dataSource={g.duplicates}
              pagination={false}
              columns={[
                { title: '重复客户', dataIndex: 'name' },
                { title: '公司', dataIndex: 'company', render: (v?: string) => v || '-' },
                {
                  title: '相似度',
                  dataIndex: 'similarity',
                  width: 90,
                  render: (v: number) => <Tag color={SIM_COLORS[v] ?? 'default'}>{v}%</Tag>,
                },
                {
                  title: '关联数据',
                  dataIndex: 'relatedCount',
                  width: 90,
                  render: (v: number) => <Tag color={v > 0 ? 'orange' : 'default'}>{v} 条</Tag>,
                },
                {
                  title: '操作',
                  width: 90,
                  render: (_, item) => (
                    <Popconfirm
                      title={`将「${item.name}」合并到「${g.primaryName}」？`}
                      description="关联数据将转移，从记录进回收站"
                      onConfirm={() => void onMerge(g, item)}
                    >
                      <Button size="small" type="primary" danger loading={merging}>
                        合并
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
