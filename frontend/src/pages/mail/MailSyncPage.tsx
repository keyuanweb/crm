import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { Alert, App, Button, Form, Input, InputNumber, Modal, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined, SyncOutlined } from '@ant-design/icons'
import {
  createMailAccount,
  deleteMailAccount,
  deleteSyncRecord,
  fetchMailAccounts,
  fetchSyncRecords,
  simulateSync,
  updateMailAccount,
} from '../../services/mailService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { MailAccount, MailSyncRecord } from '../../types/mail'

interface FormValues {
  email: string
  displayName: string
  imapHost?: string
  imapPort?: number
  smtpHost?: string
  smtpPort?: number
  enabled: boolean
  isDefaultSender: boolean
}

/** 邮件同步页（062）：账户配置 + 同步记录。 */
export default function MailSyncPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<MailAccount | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 086：删除邮箱账号按权限码收口（DELETE /mail-accounts/{id} 挂 mail_account:manage）。
  const can = usePerms([PERMS.mailAccountManage])

  const columns: ProColumns<MailAccount>[] = [
    { title: t('pages.mail.email'), dataIndex: 'email' },
    { title: t('pages.mail.displayName'), dataIndex: 'displayName', search: false },
    { title: 'IMAP', dataIndex: 'imapHost', search: false, render: (_, row) => (row.imapHost ? `${row.imapHost}:${row.imapPort}` : '-') },
    { title: 'SMTP', dataIndex: 'smtpHost', search: false, render: (_, row) => (row.smtpHost ? `${row.smtpHost}:${row.smtpPort}` : '-') },
    {
      title: t('pages.mail.status'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('common.status.active')}</Tag> : <Tag>{t('common.status.inactive')}</Tag>),
    },
    {
      title: t('pages.mail.defaultSender'),
      dataIndex: 'isDefaultSender',
      search: false,
      render: (_, row) => (row.isDefaultSender ? <Tag color="gold">{t('pages.mail.tagDefault')}</Tag> : '-'),
    },
    {
      title: t('pages.mail.action'),
      valueType: 'option',
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>{t('common.button.edit')}</a>,
        can[PERMS.mailAccountManage] ? (
          <Popconfirm key="del" title={t('pages.mail.confirmDeleteAccount')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  const openEdit = (row: MailAccount) => {
    setEditing(row)
    form.setFieldsValue({
      email: row.email,
      displayName: row.displayName,
      imapHost: row.imapHost,
      imapPort: row.imapPort,
      smtpHost: row.smtpHost,
      smtpPort: row.smtpPort,
      enabled: row.enabled,
      isDefaultSender: row.isDefaultSender,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateMailAccount(editing.id, values)
        message.success(t('common.message.saved'))
      } else {
        await createMailAccount(values)
        message.success(t('pages.mail.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.mail.msgSaveFailed')))
    }
  }

  const onDelete = async (row: MailAccount) => {
    try {
      await deleteMailAccount(row.id)
      message.success(t('pages.mail.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.mail.msgDeleteFailed')))
    }
  }

  return (
    <>
      <ProTable<MailAccount>
        headerTitle={t('pages.mail.titleAccounts')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        request={async () => {
          const items = await fetchMailAccounts()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              form.resetFields()
              form.setFieldsValue({ enabled: true, isDefaultSender: false })
              setModalOpen(true)
            }}
          >
            {t('pages.mail.createAccount')}
          </Button>,
        ]}
        expandable={{
          expandedRowRender: (row) => <SyncRecordList accountId={row.id} />,
          rowExpandable: () => true,
        }}
      />
      <Modal
        title={editing ? t('pages.mail.editAccount') : t('pages.mail.createAccount')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="email" label={t('pages.mail.email')} rules={[{ required: true, type: 'email', message: t('pages.mail.msgInvalidEmail') }]}>
            <Input placeholder="sales@corp.com" />
          </Form.Item>
          <Form.Item name="displayName" label={t('pages.mail.displayName')} rules={[{ required: true, message: t('pages.mail.msgDisplayNameRequired') }]}>
            <Input placeholder={t('pages.mail.placeholderDisplayName')} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="imapHost" label={t('pages.mail.imapHost')} style={{ flex: 2 }}>
              <Input placeholder="imap.corp.com" />
            </Form.Item>
            <Form.Item name="imapPort" label={t('pages.mail.port')} style={{ flex: 1 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="smtpHost" label={t('pages.mail.smtpHost')} style={{ flex: 2 }}>
              <Input placeholder="smtp.corp.com" />
            </Form.Item>
            <Form.Item name="smtpPort" label={t('pages.mail.port')} style={{ flex: 1 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="isDefaultSender" label={t('pages.mail.setDefaultSender')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label={t('common.status.active')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function SyncRecordList({ accountId }: { accountId: number }) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const actionRef = useRef<ActionType>()
  // 086：模拟同步（POST /mail-accounts/{id}/sync）与删除同步记录
  // （DELETE /mail-accounts/{id}/records/{recordId}）两个端点挂的都是 mail_sync:manage。
  const can = usePerms([PERMS.mailSyncManage])
  const SYNC_DIRECTION_LABELS: Record<string, string> = {
    INBOUND: t('pages.mail.directionInbound'),
    OUTBOUND: t('pages.mail.directionOutbound'),
  }
  const columns: ProColumns<MailSyncRecord>[] = [
    { title: t('pages.mail.direction'), dataIndex: 'direction', render: (_, row) => <Tag color="blue">{SYNC_DIRECTION_LABELS[row.direction] ?? row.direction}</Tag> },
    { title: t('pages.mail.subject'), dataIndex: 'subject' },
    { title: t('pages.mail.from'), dataIndex: 'fromAddress', search: false },
    { title: t('pages.mail.to'), dataIndex: 'toAddress', search: false },
    { title: t('pages.mail.status'), dataIndex: 'syncStatus', search: false, render: (_, row) => (row.syncStatus === 'SYNCED' ? <Tag color="green">{t('pages.mail.tagSynced')}</Tag> : <Tag color="red">{t('pages.mail.tagFailed')}</Tag>) },
    {
      title: t('pages.mail.time'),
      dataIndex: 'syncTime',
      search: false,
      render: (_, row) => (row.syncTime ? row.syncTime.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: t('pages.mail.action'),
      valueType: 'option',
      render: (_, row) => [
        can[PERMS.mailSyncManage] ? (
          <Popconfirm key="del" title={t('pages.mail.confirmDeleteSyncRecord')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  const onDelete = async (row: MailSyncRecord) => {
    try {
      await deleteSyncRecord(accountId, row.id)
      message.success(t('pages.mail.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.mail.msgDeleteFailed')))
    }
  }

  return (
    <>
      {/*
        诚实化：按钮已写明「模拟同步」，但下表会把后端 simulateSync 插的假记录渲染成绿色
        「已同步」——看起来像真实收信成功。本期不接 IMAP（用户已确认），但不能让模拟数据
        冒充真实结果，故在表上方明示数据来源。
      */}
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('pages.mail.demoDataNoticeTitle')}
        description={t('pages.mail.demoDataNoticeDesc')}
      />
      <ProTable<MailSyncRecord>
        headerTitle={t('pages.mail.syncRecordsTitle', { id: accountId })}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 10 }}
        request={async (params) => {
          const res = await fetchSyncRecords(accountId, params.current ?? 1, params.pageSize ?? 10)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          ...(can[PERMS.mailSyncManage]
            ? [
                <Button
                  key="sync"
                  type="primary"
                  ghost
                  icon={<SyncOutlined />}
                  onClick={async () => {
                    try {
                      await simulateSync(accountId)
                      message.success(t('pages.mail.msgSyncTriggered'))
                      actionRef.current?.reload()
                    } catch (err) {
                      message.error(extractErrorMessage(err, t('pages.mail.msgSyncFailed')))
                    }
                  }}
                >
                  {t('pages.mail.btnSimulateSync')}
                </Button>,
              ]
            : []),
        ]}
      />
    </>
  )
}
