import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../../i18n'
import { ProTable, type ProColumns } from '@ant-design/pro-components'
import { fetchAuditLogs } from '../../services/auditLogService'
import type { AuditAction, AuditEntityType, AuditLog } from '../../types/auditLog'
import dayjs from 'dayjs'

export default function AuditLogPage() {
  const { t } = useTranslation()
  const [version, setVersion] = useState(0)

  // 监听语言变化，语言切换时强制重新渲染
  useEffect(() => {
    i18n.on('languageChanged', () => setVersion((v) => v + 1))
    return () => {
      // cleanup
    }
  }, [])

  const ACTION_LABELS: Record<AuditAction, string> = {
    CREATE: t('pages.auditLog.actionCreate'),
    UPDATE: t('pages.auditLog.actionUpdate'),
    DELETE: t('pages.auditLog.actionDelete'),
    IMPORT: t('pages.auditLog.actionImport'),
    EXPORT: t('pages.auditLog.actionExport'),
    CLOSE: t('pages.auditLog.actionClose'),
    RESET_PASSWORD: t('pages.auditLog.actionResetPassword'),
    CHANGE_PASSWORD: t('pages.auditLog.actionChangePassword'),
  }

  const ENTITY_LABELS: Record<AuditEntityType, string> = {
    CUSTOMER: t('pages.auditLog.entityCustomer'),
    OPPORTUNITY: t('pages.auditLog.entityOpportunity'),
    SALES_OPPORTUNITY: t('pages.auditLog.entitySalesOpportunity'),
    USER: t('pages.auditLog.entityUser'),
  }

  const columns: ProColumns<AuditLog>[] = [
    {
      title: t('pages.auditLog.colTime'),
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    { title: t('pages.auditLog.colUser'), dataIndex: 'actorName' },
    {
      title: t('pages.auditLog.colAction'),
      dataIndex: 'action',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ACTION_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => ACTION_LABELS[row.action as AuditAction] ?? row.action,
    },
    {
      title: t('pages.auditLog.colTarget'),
      dataIndex: 'entityType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ENTITY_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => ENTITY_LABELS[row.entityType as AuditEntityType] ?? row.entityType,
    },
    { title: t('pages.auditLog.colDetails'), dataIndex: 'entityId', search: false },
    { title: t('pages.auditLog.colDetails'), dataIndex: 'detail', search: false, ellipsis: true },
  ]

  return (
    <ProTable<AuditLog>
      key={version}
      size="small"
      headerTitle={t('pages.auditLog.title')}
      rowKey="id"
      columns={columns}
      search={{ labelWidth: 'auto' }}
      pagination={{ defaultPageSize: 20 }}
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
