import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { Button, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fetchWorkflowLogs } from '../../services/workflowService'
import type { ExecutionLog } from '../../types/workflow'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

export default function WorkflowLogListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()

  const columns: ProColumns<ExecutionLog>[] = [
    { title: t('pages.workflowLog.colRule'), dataIndex: 'ruleName', render: (_, row) => row.ruleName ?? row.ruleId },
    {
      title: t('pages.workflowLog.colEvent'),
      dataIndex: 'eventType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.workflowEvent).map((code) => [code, { text: labelOf(t, ENUM_KEYS.workflowEvent, code) }]),
      ),
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.workflowEvent, row.eventType)}</Tag>,
    },
    {
      title: t('pages.workflowLog.colEntity'),
      dataIndex: 'entityType',
      search: false,
      render: (_, row) => (row.entityType ? `${row.entityType} #${row.entityId}` : '-'),
    },
    {
      title: t('pages.workflowLog.colMatched'),
      dataIndex: 'matched',
      search: false,
      render: (_, row) =>
        row.matched ? (
          <Tag color="green">{t('pages.workflowLog.matched')}</Tag>
        ) : (
          <Tag>{t('pages.workflowLog.unmatched')}</Tag>
        ),
    },
    { title: t('pages.workflowLog.colResult'), dataIndex: 'actionResult', search: false, render: (_, row) => row.actionResult ?? '-' },
    {
      title: t('pages.workflowLog.colStatus'),
      dataIndex: 'success',
      valueType: 'select',
      valueEnum: {
        true: { text: t('pages.workflowLog.statusSuccess') },
        false: { text: t('pages.workflowLog.statusFailed') },
      },
      render: (_, row) =>
        row.success ? (
          <Tag color="green">{t('pages.workflowLog.statusSuccess')}</Tag>
        ) : (
          <Tag color="red">{t('pages.workflowLog.statusFailed')}</Tag>
        ),
    },
    {
      title: t('pages.workflowLog.colError'),
      dataIndex: 'errorMessage',
      search: false,
      render: (_, row) => (row.errorMessage ? <span style={{ color: '#cf1322' }}>{row.errorMessage}</span> : '-'),
    },
    {
      title: t('pages.workflowLog.colTime'),
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => row.createdAt.replace('T', ' ').slice(0, 19),
    },
  ]

  return (
    <>
      <Link to="/workflows" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.workflowLog.btnBack')}
        </Button>
      </Link>
      <div style={{ height: 16 }} />
      <ProTable<ExecutionLog>
        size="small"
        headerTitle={t('pages.workflowLog.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchWorkflowLogs({
            ruleId: undefined,
            eventType: params.eventType,
            success: params.success === undefined ? undefined : params.success === true || params.success === 'true',
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
      />
    </>
  )
}
