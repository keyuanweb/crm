import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Space, Tabs, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createApiKey,
  createWebhook,
  deleteWebhook,
  fetchApiKeys,
  fetchWebhooks,
  revokeApiKey,
  toggleWebhook,
} from '../../services/openPlatformService'
import { extractErrorMessage } from '../../services/apiClient'
import { WEBHOOK_EVENT_LABELS, type ApiKey, type WebhookSubscription } from '../../types/openPlatform'

/** 开放平台页（055，仅 ADMIN）：API Key + Webhook。 */
export default function OpenPlatformPage() {
  const [tab, setTab] = useState('keys')
  return (
    <Tabs
      activeKey={tab}
      onChange={setTab}
      items={[
        { key: 'keys', label: 'API Key', children: <ApiKeyTab /> },
        { key: 'webhooks', label: 'Webhook', children: <WebhookTab /> },
      ]}
    />
  )
}

function ApiKeyTab() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [created, setCreated] = useState<ApiKey | null>(null)
  const [form] = Form.useForm()

  const columns: ProColumns<ApiKey>[] = [
    { title: '名称', dataIndex: 'name' },
    {
      title: 'Key 前缀',
      dataIndex: 'keyPrefix',
      render: (_, row) => <Tag>{row.keyPrefix}</Tag>,
    },
    { title: '权限范围', dataIndex: 'scopes', render: (_, row) => (row.scopes ?? []).join(', ') || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (_, row) => (row.status === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag color="red">已吊销</Tag>),
    },
    {
      title: '最后使用',
      dataIndex: 'lastUsedAt',
      search: false,
      render: (_, row) => (row.lastUsedAt ? row.lastUsedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) =>
        row.status === 'ACTIVE' ? (
          <Popconfirm
            key="revoke"
            title={`吊销「${row.name}」？吊销后立即失效。`}
            onConfirm={() => void onRevoke(row)}
          >
            <a style={{ color: '#ff4d4f' }}>吊销</a>
          </Popconfirm>
        ) : (
          <span key="revoked" style={{ color: '#bbb' }}>已吊销</span>
        ),
    },
  ]

  const onRevoke = async (row: ApiKey) => {
    try {
      await revokeApiKey(row.id)
      message.success('已吊销')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '吊销失败'))
    }
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      const key = await createApiKey({
        name: values.name.trim(),
        scopes: values.scopes ?? [],
      })
      setCreated(key)
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    }
  }

  return (
    <>
      <ProTable<ApiKey>
        headerTitle="API Key"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchApiKeys(params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>
            新建 API Key
          </Button>,
        ]}
      />
      <Modal title="新建 API Key" open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText="创建" destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：数据同步" />
          </Form.Item>
          <Form.Item name="scopes" label="权限范围" initialValue={['customer:read']}>
            <Select
              mode="multiple"
              options={[
                { value: 'customer:read', label: '客户只读' },
                { value: 'lead:read', label: '线索只读' },
                { value: 'lead:write', label: '线索写入' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="API Key 已创建"
        open={!!created}
        onCancel={() => setCreated(null)}
        footer={
          <Button type="primary" onClick={() => setCreated(null)}>
            我已保存
          </Button>
        }
      >
        <Typography.Paragraph type="danger">
          请立即复制并妥善保存，此完整 Key 仅显示一次：
        </Typography.Paragraph>
        <Input value={created?.key} readOnly />
      </Modal>
    </>
  )
}

function WebhookTab() {
  const { message } = App.useApp()
  const [webhooks, setWebhooks] = useState<WebhookSubscription[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [createdSecret, setCreatedSecret] = useState<WebhookSubscription | null>(null)
  const [form] = Form.useForm()

  const reload = () => {
    void fetchWebhooks().then(setWebhooks).catch(() => undefined)
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      const sub = await createWebhook({ eventType: values.eventType, callbackUrl: values.callbackUrl.trim() })
      setCreatedSecret(sub)
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>
        新建 Webhook
      </Button>
      {webhooks.map((w) => (
        <div key={w.id} style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: '12px 16px' }}>
          <Space size={16}>
            <Tag color="blue">{WEBHOOK_EVENT_LABELS[w.eventType] ?? w.eventType}</Tag>
            <Typography.Text code>{w.callbackUrl}</Typography.Text>
            {w.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
            <Button size="small" onClick={() => void toggle(w.id)}>
              {w.enabled ? '停用' : '启用'}
            </Button>
            <Popconfirm title="删除订阅？" onConfirm={() => void remove(w.id)}>
              <Button size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        </div>
      ))}
      <Modal title="新建 Webhook" open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText="创建" destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="eventType" label="事件类型" rules={[{ required: true, message: '请选择事件' }]}>
            <Select options={Object.entries(WEBHOOK_EVENT_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Form.Item name="callbackUrl" label="回调 URL" rules={[{ required: true, message: '请输入回调 URL' }]}>
            <Input placeholder="https://your-system.com/webhook" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="Webhook 已创建"
        open={!!createdSecret}
        onCancel={() => setCreatedSecret(null)}
        footer={<Button type="primary" onClick={() => setCreatedSecret(null)}>我已保存</Button>}
      >
        <Typography.Paragraph type="danger">签名密钥（仅显示一次，回调验签用）：</Typography.Paragraph>
        <Input value={createdSecret?.secret} readOnly />
      </Modal>
    </Space>
  )

  async function toggle(id: number) {
    try {
      await toggleWebhook(id)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  async function remove(id: number) {
    try {
      await deleteWebhook(id)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }
}
