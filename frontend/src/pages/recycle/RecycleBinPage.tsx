import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Input, Popconfirm, Select, Tag, Typography } from 'antd'
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons'
import { fetchRecycleBin, purgeItems, restoreItems } from '../../services/recycleService'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { RecycleItem } from '../../types/recycle'

const { Title, Paragraph } = Typography

const TYPE_META: Record<string, { label: string; color: string }> = {
  CUSTOMER: { label: 'customers', color: 'blue' },
  LEAD: { label: 'leads', color: 'cyan' },
  CONTACT: { label: 'contacts', color: 'purple' },
  OPPORTUNITY: { label: 'opportunities', color: 'gold' },
}

export default function RecycleBinPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [type, setType] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<RecycleItem[]>([])
  // 同页两个按钮挂**不同的码**：恢复走 POST /recycle-bin/restore（recycle:restore），
  // 彻底删除走 POST /recycle-bin/purge（recycle:purge，不可逆）——不得一刀切。
  const can = usePerms([PERMS.recycleRestore, PERMS.recyclePurge])

  const onRestore = async () => {
    if (!selected.length) return
    const res = await restoreItems(selected.map((s) => ({ type: s.type, id: s.id })))
    message.success(`${t('pages.recycleBin.msgRestored')} ${res.restoredCount} 条${res.failures.length ? `，${res.failures.length} 条失败` : ''}`)
    setSelected([])
    actionRef.current?.reload()
  }

  const onPurge = async () => {
    if (!selected.length) return
    const res = await purgeItems(selected.map((s) => ({ type: s.type, id: s.id })))
    message.success(`${t('pages.recycleBin.msgDeleted')} ${res.purgedCount} 条`)
    setSelected([])
    actionRef.current?.reload()
  }

  const columns: ProColumns<RecycleItem>[] = [
    {
      title: t('pages.recycleBin.colType'),
      dataIndex: 'type',
      width: 100,
      render: (_, row) => {
        const m = TYPE_META[row.type] ?? { label: row.type, color: 'default' }
        return <Tag color={m.color}>{t(`menu.${m.label}`)}</Tag>
      },
    },
    { title: t('pages.recycleBin.colName'), dataIndex: 'name', render: (_, row) => row.name ?? `#${row.id}` },
    {
      title: t('pages.recycleBin.colDeletedAt'),
      dataIndex: 'deletedAt',
      width: 180,
      render: (_, row) => (row.deletedAt ? row.deletedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    { title: t('pages.recycleBin.colDeletedBy'), dataIndex: 'deletedBy', width: 100, render: (_, row) => `${t('pages.recycleBin.colDeletedByLabel')}#${row.deletedBy ?? '-'}` },
  ]

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Title level={4} style={{ marginBottom: 4 }}>
          {t('pages.recycleBin.title')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.recycleBin.description')}
        </Paragraph>
      </div>

      <ProTable<RecycleItem>
        size="small"
        headerTitle={t('pages.recycleBin.headerTitle')}
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
            placeholder={t('pages.recycleBin.colType')}
            value={type}
            onChange={(v) => {
              setType(v)
              actionRef.current?.reload()
            }}
            style={{ width: 120 }}
            options={Object.entries(TYPE_META).map(([value, m]) => ({ value, label: t(`menu.${m.label}`) }))}
          />,
          <Input.Search
            key="keyword"
            placeholder={t('pages.recycleBin.searchPlaceholder')}
            allowClear
            style={{ width: 200 }}
            onSearch={(v) => {
              setKeyword(v)
              actionRef.current?.reload()
            }}
          />,
          ...(can[PERMS.recycleRestore]
            ? [
                <Popconfirm key="restore" title={t('pages.recycleBin.confirmRestore')} onConfirm={() => void onRestore()}>
                  <Button type="primary" icon={<ReloadOutlined />} disabled={!selected.length}>
                    {t('pages.recycleBin.btnRestore')}
                  </Button>
                </Popconfirm>,
              ]
            : []),
          ...(can[PERMS.recyclePurge]
            ? [
                <Popconfirm
                  key="purge"
                  title={t('pages.recycleBin.confirmPurge')}
                  onConfirm={() => void onPurge()}
                >
                  <Button danger icon={<DeleteOutlined />} disabled={!selected.length}>
                    {t('pages.recycleBin.btnDelete')}
                  </Button>
                </Popconfirm>,
              ]
            : []),
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
