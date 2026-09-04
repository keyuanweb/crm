import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { Button, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fetchWorkflowLogs } from '../../services/workflowService'
import { EVENT_LABELS } from '../../types/workflow'
import type { ExecutionLog } from '../../types/workflow'

export default function WorkflowLogListPage() {
  const actionRef = useRef<ActionType>()

  const columns: ProColumns<ExecutionLog>[] = [
    { title: '规则', dataIndex: 'ruleName', render: (_, row) => row.ruleName ?? row.ruleId },
    {
      title: '事件',
      dataIndex: 'eventType',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(EVENT_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag color="blue">{EVENT_LABELS[row.eventType as keyof typeof EVENT_LABELS] ?? row.eventType}</Tag>,
    },
    {
      title: '实体',
      dataIndex: 'entityType',
      search: false,
      render: (_, row) => (row.entityType ? `${row.entityType} #${row.entityId}` : '-'),
    },
    {
      title: '匹配',
      dataIndex: 'matched',
      search: false,
      render: (_, row) => (row.matched ? <Tag color="green">匹配</Tag> : <Tag>未匹配</Tag>),
    },
    { title: '结果', dataIndex: 'actionResult', search: false, render: (_, row) => row.actionResult ?? '-' },
    {
      title: '状态',
      dataIndex: 'success',
      valueType: 'select',
      valueEnum: { true: { text: '成功' }, false: { text: '失败' } },
      render: (_, row) =>
        row.success ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      search: false,
      render: (_, row) => (row.errorMessage ? <span style={{ color: '#cf1322' }}>{row.errorMessage}</span> : '-'),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => row.createdAt.replace('T', ' ').slice(0, 19),
    },
  ]

  return (
    <>
      <Link to="/workflows" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回规则管理
        </Button>
      </Link>
      <div style={{ height: 16 }} />
      <ProTable<ExecutionLog>
        size="small"
        headerTitle="工作流执行日志"
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
