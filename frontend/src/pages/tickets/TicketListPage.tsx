import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, Form, Input, Modal, Row, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createTicket,
  fetchTickets,
  type TicketPayload,
} from '../../services/ticketService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { extractCfParams, useCustomFieldFilterColumns } from '../../hooks/useCustomFieldFilters'
import {
  TICKET_PRIORITY_COLORS,
  TICKET_PRIORITY_LABELS,
  TICKET_SLA_COLORS,
  TICKET_SLA_LABELS,
  TICKET_STATUS_COLORS,
  TICKET_STATUS_LABELS,
  type Ticket,
  type TicketPriority,
  type TicketStatus,
} from '../../types/ticket'
import {
  toCustomFieldPayload,
} from '../../utils/customField'
import { CustomFieldFormItems } from '../../components/CustomFieldItems'

interface FormValues {
  customerId: number
  title: string
  description?: string
  priority: TicketPriority
  customFieldValues?: Record<string, string | number | undefined>
  remark?: string
}

export default function TicketListPage() {
  const actionRef = useRef<ActionType>()
  const customFieldFilterColumns = useCustomFieldFilterColumns('TICKET')
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    form.resetFields()
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: TicketPayload = {
      customerId: values.customerId,
      title: values.title.trim(),
      description: values.description,
      priority: values.priority,
      remark: values.remark,
      customFieldValues: toCustomFieldPayload(values.customFieldValues as Record<string, unknown>),
    }
    setSaving(true)
    try {
      await createTicket(payload)
      message.success('已创建')
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const loadCustomers = async (keyword?: string) => {
    const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
    setCustomerOptions(res.items.map((c) => ({ value: c.id, label: `${c.name}（${c.company}）` })))
  }

  const slaRender = (_: unknown, row: Ticket) => {
    if (!row.slaStatus) return '-'
    return <Tag color={TICKET_SLA_COLORS[row.slaStatus]}>{TICKET_SLA_LABELS[row.slaStatus]}</Tag>
  }

  const columns: ProColumns<Ticket>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_, row) => <Link to={`/tickets/${row.id}`}>{row.title}</Link>,
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      search: false,
      render: (_, row) => row.customerName ?? `#${row.customerId}`,
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(TICKET_PRIORITY_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color={TICKET_PRIORITY_COLORS[row.priority]}>{TICKET_PRIORITY_LABELS[row.priority]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(TICKET_STATUS_LABELS).map(([k, v]) => [
          k,
          { text: v, status: TICKET_STATUS_COLORS[k as TicketStatus] },
        ]),
      ),
    },
    { title: '处理人', dataIndex: 'assigneeName', search: false },
    { title: 'SLA', dataIndex: 'slaStatus', search: false, render: slaRender },
    { title: '回复数', dataIndex: 'replyCount', search: false },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        <Link key="detail" to={`/tickets/${row.id}`}>
          详情
        </Link>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Ticket>
        size="small"
        headerTitle="服务工单"
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchTickets({
            keyword: params.keyword,
            status: params.status,
            priority: params.priority,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
            ...extractCfParams(params as Record<string, unknown>),
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增工单
          </Button>,
        ]}
      />

      <Modal
        title="新增工单"
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="创建"
        confirmLoading={saving}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="ticketForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="customerId"
                label="客户"
                rules={[{ required: true, message: '请选择客户' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="搜索并选择客户"
                  options={customerOptions}
                  onSearch={loadCustomers}
                  onFocus={() => void loadCustomers()}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
                <Input maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label="问题描述">
                <Input.TextArea rows={4} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
                <Select
                  options={Object.entries(TICKET_PRIORITY_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <CustomFieldFormItems entityType="TICKET" />
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
