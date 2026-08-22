import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { fetchAuditLogs } from '../../services/auditLogService'
import {
  ACTION_LABELS,
  ENTITY_LABELS,
  type AuditAction,
  type AuditEntityType,
  type AuditLog,
} from '../../types/auditLog'
import dayjs from 'dayjs'

export default function AuditLogPage() {
  const columns: ProColumns<AuditLog>[] = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: '操作人', dataIndex: 'actorName' },
    {
      title: '动作',
      dataIndex: 'action',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ACTION_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => ACTION_LABELS[row.action as AuditAction] ?? row.action,
    },
    {
      title: '对象',
      dataIndex: 'entityType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ENTITY_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => ENTITY_LABELS[row.entityType as AuditEntityType] ?? row.entityType,
    },
    { title: '对象 ID', dataIndex: 'entityId', search: false },
    { title: '说明', dataIndex: 'detail', search: false, ellipsis: true },
  ]

  return (
    <ProTable<AuditLog>
      headerTitle="审计日志"
      rowKey="id"
      columns={columns}
      search={{ labelWidth: 'auto' }}
      pagination={{ defaultPageSize: 20 }}
      cardProps={{ style: { borderRadius: 10 } }}
      request={async (params) => {
        const res = await fetchAuditLogs({
          action: params.action,
          entityType: params.entityType,
          actorName: params.actorName,
          page: params.current ?? 1,
          pageSize: params.pageSize ?? 20,
        })
        return { data: res.items, success: true, total: res.total }
      }}
    />
  )
}
