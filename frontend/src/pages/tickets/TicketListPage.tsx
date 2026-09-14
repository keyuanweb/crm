import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
  TICKET_SLA_COLORS,
  TICKET_STATUS_COLORS,
  type Ticket,
  type TicketPriority,
  type TicketStatus,
} from '../../types/ticket'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
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
  const { t } = useTranslation()
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
      message.success(t('pages.ticket.list.msgCreated'))
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
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
    return <Tag color={TICKET_SLA_COLORS[row.slaStatus]}>{labelOf(t, ENUM_KEYS.ticketSla, row.slaStatus)}</Tag>
  }

  const columns: ProColumns<Ticket>[] = [
    {
      title: t('pages.ticket.list.colTitle'),
      dataIndex: 'title',
      render: (_, row) => <Link to={`/tickets/${row.id}`}>{row.title}</Link>,
    },
    {
      title: t('pages.ticket.list.colCustomer'),
      dataIndex: 'customerName',
      search: false,
      render: (_, row) => row.customerName ?? `#${row.customerId}`,
    },
    {
      title: t('pages.ticket.list.colPriority'),
      dataIndex: 'priority',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.ticketPriority).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.ticketPriority, code) },
        ]),
      ),
      render: (_, row) => (
        <Tag color={TICKET_PRIORITY_COLORS[row.priority]}>
          {labelOf(t, ENUM_KEYS.ticketPriority, row.priority)}
        </Tag>
      ),
    },
    {
      title: t('pages.ticket.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.ticketStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.ticketStatus, code), status: TICKET_STATUS_COLORS[code as TicketStatus] },
        ]),
      ),
    },
    { title: t('pages.ticket.list.colAssignee'), dataIndex: 'assigneeName', search: false },
    { title: 'SLA', dataIndex: 'slaStatus', search: false, render: slaRender },
    { title: t('pages.ticket.list.colReplies'), dataIndex: 'replyCount', search: false },
    {
      title: t('pages.ticket.list.colCreated'),
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: t('pages.ticket.list.colAction'),
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        <Link key="detail" to={`/tickets/${row.id}`}>
          {t('pages.ticket.list.detail')}
        </Link>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Ticket>
        size="small"
        headerTitle={t('pages.ticket.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={[...columns, ...customFieldFilterColumns]}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
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
            {t('pages.ticket.list.create')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.ticket.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.create')}
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
                label={t('pages.ticket.list.formCustomer')}
                rules={[{ required: true, message: t('pages.ticket.list.msgCustomerRequired') }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('pages.contract.list.phCustomer')}
                  options={customerOptions}
                  onSearch={loadCustomers}
                  onFocus={() => void loadCustomers()}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label={t('pages.ticket.list.formTitle')} rules={[{ required: true, message: t('pages.ticket.list.msgTitleRequired') }]}>
                <Input maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label={t('pages.ticket.list.formDesc')}>
                <Input.TextArea rows={4} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="priority" label={t('pages.ticket.list.formPriority')} rules={[{ required: true, message: t('pages.ticket.list.msgPriorityRequired') }]}>
                <Select
                  options={Object.keys(ENUM_KEYS.ticketPriority).map((code) => ({
                    value: code,
                    label: labelOf(t, ENUM_KEYS.ticketPriority, code),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <CustomFieldFormItems entityType="TICKET" />
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label={t('pages.opportunity.list.formRemark')}>
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
