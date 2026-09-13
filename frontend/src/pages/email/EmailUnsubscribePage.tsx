import { useRef } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Popconfirm } from 'antd'
import { useTranslation } from 'react-i18next'
import {
  fetchUnsubscribes,
  restoreUnsubscribe,
  type EmailUnsubscribe,
} from '../../services/emailService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'

/** 邮件退订名单页（052）。 */
export default function EmailUnsubscribePage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const { t } = useTranslation()
  // 086：恢复（DELETE /email/unsubscribes/{id}）按权限码收口。
  const can = usePerms([PERMS.emailManage])

  const columns: ProColumns<EmailUnsubscribe>[] = [
    { title: t('pages.emailUnsubscribe.colEmail'), dataIndex: 'email', copyable: true },
    {
      title: t('pages.emailUnsubscribe.colCampaign'),
      dataIndex: 'campaignId',
      search: false,
      render: (_, row) => (row.campaignId ? `#${row.campaignId}` : '-'),
    },
    {
      title: t('pages.emailUnsubscribe.colUnsubscribedAt'),
      dataIndex: 'unsubscribedAt',
      search: false,
      render: (_, row) => row.unsubscribedAt.replace('T', ' ').slice(0, 19),
    },
    {
      title: t('pages.emailUnsubscribe.colAction'),
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        can[PERMS.emailManage] ? (
          <Popconfirm
            key="restore"
            title={t('pages.emailUnsubscribe.confirmRestore', { email: row.email })}
            onConfirm={async () => {
              try {
                await restoreUnsubscribe(row.id)
                message.success(t('pages.emailUnsubscribe.msgRestored'))
                actionRef.current?.reload()
              } catch (err) {
                message.error(extractErrorMessage(err, t('pages.emailUnsubscribe.msgRestoreFailed')))
              }
            }}
          >
            <a>{t('pages.emailUnsubscribe.btnRestore')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <ProTable<EmailUnsubscribe>
      headerTitle={t('pages.emailUnsubscribe.title')}
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
