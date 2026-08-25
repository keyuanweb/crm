import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Switch, Tag } from 'antd'
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
import { SYNC_DIRECTION_LABELS, type MailAccount, type MailSyncRecord } from '../../types/mail'

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
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<MailAccount | null>(null)
  const [form] = Form.useForm<FormValues>()

  const columns: ProColumns<MailAccount>[] = [
    { title: '邮箱', dataIndex: 'email' },
    { title: '显示名', dataIndex: 'displayName', search: false },
    { title: 'IMAP', dataIndex: 'imapHost', search: false, render: (_, row) => (row.imapHost ? `${row.imapHost}:${row.imapPort}` : '-') },
    { title: 'SMTP', dataIndex: 'smtpHost', search: false, render: (_, row) => (row.smtpHost ? `${row.smtpHost}:${row.smtpPort}` : '-') },
    {
      title: '状态',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '默认发件',
      dataIndex: 'isDefaultSender',
      search: false,
      render: (_, row) => (row.isDefaultSender ? <Tag color="gold">默认</Tag> : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>编辑</a>,
        <Popconfirm key="del" title="删除该账户？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
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
        message.success('已保存')
      } else {
        await createMailAccount(values)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: MailAccount) => {
    try {
      await deleteMailAccount(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <ProTable<MailAccount>
        headerTitle="邮件账户"
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
            新增账户
          </Button>,
        ]}
        expandable={{
          expandedRowRender: (row) => <SyncRecordList accountId={row.id} />,
          rowExpandable: () => true,
        }}
      />
      <Modal
        title={editing ? '编辑账户' : '新增账户'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email', message: '请输入合法邮箱' }]}>
            <Input placeholder="sales@corp.com" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true, message: '请输入显示名' }]}>
            <Input placeholder="销售部" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="imapHost" label="IMAP 主机" style={{ flex: 2 }}>
              <Input placeholder="imap.corp.com" />
            </Form.Item>
            <Form.Item name="imapPort" label="端口" style={{ flex: 1 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="smtpHost" label="SMTP 主机" style={{ flex: 2 }}>
              <Input placeholder="smtp.corp.com" />
            </Form.Item>
            <Form.Item name="smtpPort" label="端口" style={{ flex: 1 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="isDefaultSender" label="设为默认发件" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function SyncRecordList({ accountId }: { accountId: number }) {
  const { message } = App.useApp()
  const actionRef = useRef<ActionType>()
  const columns: ProColumns<MailSyncRecord>[] = [
    { title: '方向', dataIndex: 'direction', render: (_, row) => <Tag color="blue">{SYNC_DIRECTION_LABELS[row.direction] ?? row.direction}</Tag> },
    { title: '主题', dataIndex: 'subject' },
    { title: '发件人', dataIndex: 'fromAddress', search: false },
    { title: '收件人', dataIndex: 'toAddress', search: false },
    { title: '状态', dataIndex: 'syncStatus', search: false, render: (_, row) => (row.syncStatus === 'SYNCED' ? <Tag color="green">已同步</Tag> : <Tag color="red">失败</Tag>) },
    {
      title: '时间',
      dataIndex: 'syncTime',
      search: false,
      render: (_, row) => (row.syncTime ? row.syncTime.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <Popconfirm key="del" title="删除该同步记录？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  const onDelete = async (row: MailSyncRecord) => {
    try {
      await deleteSyncRecord(accountId, row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <ProTable<MailSyncRecord>
      headerTitle={`同步记录 · 账户 #${accountId}`}
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
        <Button
          key="sync"
          type="primary"
          ghost
          icon={<SyncOutlined />}
          onClick={async () => {
            try {
              await simulateSync(accountId)
              message.success('已触发模拟同步')
              actionRef.current?.reload()
            } catch (err) {
              message.error(extractErrorMessage(err, '同步失败'))
            }
          }}
        >
          模拟同步
        </Button>,
      ]}
    />
  )
}
