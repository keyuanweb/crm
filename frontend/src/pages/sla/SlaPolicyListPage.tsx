import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, InputNumber, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createSlaPolicy,
  deleteSlaPolicy,
  fetchSlaOverview,
  fetchSlaPolicies,
  updateSlaPolicy,
  type SlaPolicyPayload,
} from '../../services/slaService'
import { extractErrorMessage } from '../../services/apiClient'
import { TICKET_PRIORITY_LABELS, type TicketPriority } from '../../types/ticket'
import type { SlaPolicy } from '../../types/sla'

interface FormValues {
  priority: TicketPriority
  respondHours?: number
  resolveHours?: number
  enabled: boolean
}

export default function SlaPolicyListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<SlaPolicy | null>(null)
  const [overview, setOverview] = useState<{ totalOpen: number; overdue: number } | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const loadOverview = async () => {
    try {
      const o = await fetchSlaOverview()
      setOverview(o)
    } catch {
      // 忽略统计加载失败
    }
  }

  useEffect(() => {
    void loadOverview()
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ enabled: true })
    setModalOpen(true)
  }

  const openEdit = (row: SlaPolicy) => {
    setEditing(row)
    form.setFieldsValue({
      priority: row.priority as TicketPriority,
      respondHours: row.respondHours,
      resolveHours: row.resolveHours,
      enabled: row.enabled === 1,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: SlaPolicyPayload = {
      priority: values.priority,
      respondHours: values.respondHours,
      resolveHours: values.resolveHours,
      enabled: values.enabled ? 1 : 0,
    }
    try {
      if (editing) {
        await updateSlaPolicy(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createSlaPolicy(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
      void loadOverview()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: SlaPolicy) => {
    try {
      await deleteSlaPolicy(row.id)
      message.success('已删除')
      reload()
      void loadOverview()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<SlaPolicy>[] = [
    {
      title: '优先级',
      dataIndex: 'priority',
      render: (_, row) => <Tag color="blue">{TICKET_PRIORITY_LABELS[row.priority]}</Tag>,
    },
    {
      title: '响应时限（小时）',
      dataIndex: 'respondHours',
      search: false,
      render: (_, row) => row.respondHours ?? '不约束',
    },
    {
      title: '解决时限（小时）',
      dataIndex: 'resolveHours',
      search: false,
      render: (_, row) => row.resolveHours ?? '不约束',
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) =>
        row.enabled === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除「${TICKET_PRIORITY_LABELS[row.priority]}」策略吗？`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Tag color={overview && overview.overdue > 0 ? 'red' : 'green'} style={{ fontSize: 13, padding: '4px 10px' }}>
          未关闭工单 {overview?.totalOpen ?? 0} 张，已超时 {overview?.overdue ?? 0} 张
        </Tag>
      </div>

      <ProTable<SlaPolicy>
        headerTitle="SLA 策略"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 10 }}
        request={async (params) => {
          const res = await fetchSlaPolicies(params.current ?? 1, params.pageSize ?? 10)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增策略
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑 SLA 策略' : '新增 SLA 策略'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={520}
      >
        <Form form={form} name="slaForm" layout="vertical">
          <Form.Item
            name="priority"
            label="优先级"
            rules={[{ required: true, message: '请选择优先级' }]}
          >
            <Select
              disabled={!!editing}
              options={Object.entries(TICKET_PRIORITY_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="respondHours" label="响应时限（小时，可空）" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} placeholder="不填则不约束" />
            </Form.Item>
            <Form.Item name="resolveHours" label="解决时限（小时，可空）" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} placeholder="不填则不约束" />
            </Form.Item>
          </div>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
