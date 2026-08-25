import { useRef } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Popconfirm } from 'antd'
import {
  fetchUnsubscribes,
  restoreUnsubscribe,
  type EmailUnsubscribe,
} from '../../services/emailService'
import { extractErrorMessage } from '../../services/apiClient'

/** 邮件退订名单页（052）。 */
export default function EmailUnsubscribePage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()

  const columns: ProColumns<EmailUnsubscribe>[] = [
    { title: '邮箱', dataIndex: 'email', copyable: true },
    {
      title: '来源活动',
      dataIndex: 'campaignId',
      search: false,
      render: (_, row) => (row.campaignId ? `#${row.campaignId}` : '-'),
    },
    {
      title: '退订时间',
      dataIndex: 'unsubscribedAt',
      search: false,
      render: (_, row) => row.unsubscribedAt.replace('T', ' ').slice(0, 19),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        <Popconfirm
          key="restore"
          title={`恢复 ${row.email}（取消退订）？`}
          onConfirm={async () => {
            try {
              await restoreUnsubscribe(row.id)
              message.success('已恢复')
              actionRef.current?.reload()
            } catch (err) {
              message.error(extractErrorMessage(err, '恢复失败'))
            }
          }}
        >
          <a>恢复</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <ProTable<EmailUnsubscribe>
      headerTitle="邮件退订名单"
      rowKey="id"
      actionRef={actionRef}
      columns={columns}
      search={{ labelWidth: 'auto' }}
      pagination={{ defaultPageSize: 20 }}
      request={async (params) => {
        const res = await fetchUnsubscribes(params.keyword, params.current ?? 1, params.pageSize ?? 20)
        return { data: res.items, success: true, total: res.total }
      }}
    />
  )
}
