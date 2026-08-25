import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Drawer, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createChannel,
  deleteChannel,
  fetchChannelDeliveries,
  fetchChannels,
  toggleChannel,
  updateChannel,
} from '../../services/integrationService'
import { extractErrorMessage } from '../../services/apiClient'
import { CHANNEL_TYPE_LABELS, type IntegrationChannel } from '../../types/integration'

interface FormValues {
  channelType: string
  name: string
  webhookUrl: string
  enabled: boolean
}

/** 集成中心页（058，仅 ADMIN）：通道管理 + 推送记录。 */
export default function IntegrationHubPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<IntegrationChannel | null>(null)
  const [deliveryChannel, setDeliveryChannel] = useState<IntegrationChannel | null>(null)
  const [form] = Form.useForm<FormValues>()

  const columns: ProColumns<IntegrationChannel>[] = [
    {
      title: '类型',
      dataIndex: 'channelType',
      valueEnum: Object.fromEntries(Object.entries(CHANNEL_TYPE_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag color="blue">{CHANNEL_TYPE_LABELS[row.channelType] ?? row.channelType}</Tag>,
    },
    { title: '名称', dataIndex: 'name' },
    { title: 'Webhook URL', dataIndex: 'webhookUrl', search: false, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <a key="deliveries" onClick={() => setDeliveryChannel(row)}>
          推送记录
        </a>,
        <a key="toggle" onClick={() => void onToggle(row)}>
          {row.enabled ? '停用' : '启用'}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="del" title="删除该通道？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  const openEdit = (row: IntegrationChannel) => {
    setEditing(row)
    form.setFieldsValue({ channelType: row.channelType, name: row.name, webhookUrl: row.webhookUrl, enabled: row.enabled })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateChannel(editing.id, values)
        message.success('已保存')
      } else {
        await createChannel(values)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onToggle = async (row: IntegrationChannel) => {
    try {
      await toggleChannel(row.id)
      message.success(row.enabled ? '已停用' : '已启用')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: IntegrationChannel) => {
    try {
      await deleteChannel(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <ProTable<IntegrationChannel>
        headerTitle="集成通道"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={false}
        request={async () => {
          const items = await fetchChannels()
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
              form.setFieldsValue({ enabled: true })
              setModalOpen(true)
            }}
          >
            新增通道
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑通道' : '新增通道'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="channelType" label="通道类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select options={Object.entries(CHANNEL_TYPE_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Form.Item name="name" label="通道名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：销售群" maxLength={100} />
          </Form.Item>
          <Form.Item name="webhookUrl" label="Webhook URL" rules={[{ required: true, message: '请输入 URL' }]}>
            <Input placeholder="https://qyapi.weixin.qq.com/..." />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        title={`推送记录 · ${deliveryChannel?.name ?? ''}`}
        open={!!deliveryChannel}
        onClose={() => setDeliveryChannel(null)}
        width={520}
      >
        {deliveryChannel && <DeliveryList channelId={deliveryChannel.id} />}
      </Drawer>
    </>
  )
}

function DeliveryList({ channelId }: { channelId: number }) {
  const [records, setRecords] = useState<{ id: number; eventType: string; status: string; httpStatus?: number; error?: string; createdAt: string }[]>([])
  useEffect(() => {
    void fetchChannelDeliveries(channelId, 1, 50)
      .then((res) => setRecords(res.items))
      .catch(() => undefined)
  }, [channelId])

  return (
    <div>
      {records.length === 0 ? (
        <span style={{ color: '#8c8c8c' }}>暂无推送记录</span>
      ) : (
        records.map((r) => (
          <div key={r.id} style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8 }}>
            <Tag color="blue">{r.eventType}</Tag>
            {r.status === 'SUCCESS' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>}
            <span style={{ color: '#8c8c8c', marginLeft: 8 }}>{r.createdAt.replace('T', ' ').slice(0, 19)}</span>
            {r.error && <div style={{ color: '#cf1322', marginTop: 4 }}>{r.error}</div>}
          </div>
        ))
      )}
    </div>
  )
}
