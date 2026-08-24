import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Input, Popconfirm, Select, Tag, Typography } from 'antd'
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons'
import { fetchRecycleBin, purgeItems, restoreItems } from '../../services/recycleService'
import type { RecycleItem } from '../../types/recycle'

const { Title, Paragraph } = Typography

const TYPE_META: Record<string, { label: string; color: string }> = {
  CUSTOMER: { label: '客户', color: 'blue' },
  LEAD: { label: '线索', color: 'cyan' },
  CONTACT: { label: '联系人', color: 'purple' },
  OPPORTUNITY: { label: '商机', color: 'gold' },
}

export default function RecycleBinPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [type, setType] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<RecycleItem[]>([])

  const onRestore = async () => {
    if (!selected.length) return
    const res = await restoreItems(selected.map((s) => ({ type: s.type, id: s.id })))
    message.success(`已恢复 ${res.restoredCount} 条${res.failures.length ? `，${res.failures.length} 条失败` : ''}`)
    setSelected([])
    actionRef.current?.reload()
  }

  const onPurge = async () => {
    if (!selected.length) return
    const res = await purgeItems(selected.map((s) => ({ type: s.type, id: s.id })))
    message.success(`已彻底删除 ${res.purgedCount} 条`)
    setSelected([])
    actionRef.current?.reload()
  }

  const columns: ProColumns<RecycleItem>[] = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (_, row) => {
        const m = TYPE_META[row.type] ?? { label: row.type, color: 'default' }
        return <Tag color={m.color}>{m.label}</Tag>
      },
    },
    { title: '名称', dataIndex: 'name', render: (_, row) => row.name ?? `#${row.id}` },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 180,
      render: (_, row) => (row.deletedAt ? row.deletedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    { title: '删除人', dataIndex: 'deletedBy', width: 100, render: (_, row) => `用户#${row.deletedBy ?? '-'}` },
  ]

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          回收站
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          管理已逻辑删除的客户/线索/联系人/商机，支持批量恢复与彻底删除。
        </Paragraph>
      </div>

      <ProTable<RecycleItem>
        size="small"
        headerTitle="已删除数据"
        rowKey={(r) => `${r.type}-${r.id}`}
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        rowSelection={{ selectedRowKeys: selected.map((s) => `${s.type}-${s.id}`), onChange: (_keys, rows) => setSelected(rows) }}
        toolBarRender={() => [
          <Select
            key="type"
            allowClear
            placeholder="类型"
            value={type}
            onChange={(v) => {
              setType(v)
              actionRef.current?.reload()
            }}
            style={{ width: 120 }}
            options={Object.entries(TYPE_META).map(([value, m]) => ({ value, label: m.label }))}
          />,
          <Input.Search
            key="keyword"
            placeholder="搜索名称"
            allowClear
            style={{ width: 200 }}
            onSearch={(v) => {
              setKeyword(v)
              actionRef.current?.reload()
            }}
          />,
          <Popconfirm key="restore" title="确认恢复选中的记录？" onConfirm={() => void onRestore()}>
            <Button type="primary" icon={<ReloadOutlined />} disabled={!selected.length}>
              恢复
            </Button>
          </Popconfirm>,
          <Popconfirm
            key="purge"
            title="确认彻底删除？此操作不可恢复"
            onConfirm={() => void onPurge()}
          >
            <Button danger icon={<DeleteOutlined />} disabled={!selected.length}>
              彻底删除
            </Button>
          </Popconfirm>,
        ]}
        request={async (params) => {
          const res = await fetchRecycleBin({
            type,
            keyword,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
      />
    </div>
  )
}
