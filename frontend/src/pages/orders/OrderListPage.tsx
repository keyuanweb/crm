import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createOrder,
  deleteOrder,
  fetchOrders,
  type OrderPayload,
} from '../../services/orderService'
import { fetchCustomers } from '../../services/customerService'
import { fetchContracts } from '../../services/contractService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import { hasPerm } from '../../hooks/usePermission'
import { PERMS } from '../../constants/permissions'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { ORDER_STATUS_COLORS, type Order, type PlanItemPayload } from '../../types/order'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH, useFormMetrics } from '../../components/ui'

interface FormValues {
  title: string
  customerId: number
  contractId?: number
  amount?: number
  description?: string
}

interface PlanFormValues {
  amount: number
  dueDate?: Dayjs
  description?: string
}

export default function OrderListPage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [planForm] = Form.useForm<PlanFormValues>()
  const [plans, setPlans] = useState<PlanItemPayload[]>([])
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [contractOptions, setContractOptions] = useState<{ value: number; label: string }[]>([])
  const [planModalOpen, setPlanModalOpen] = useState(false)
  const user = useAuthStore((s) => s.user)
  // 删除订单按权限码而不是角色名：OrderController.delete 标的是 order:delete。
  const canDelete = hasPerm(PERMS.orderDelete, user)

  const reload = () => actionRef.current?.reload()

  const loadCustomers = async (keyword?: string) => {
    const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
    setCustomerOptions(res.items.map((c) => ({ value: c.id, label: c.company || c.name })))
  }

  const loadContracts = async () => {
    const res = await fetchContracts({ status: 'EFFECTIVE', page: 1, pageSize: 50 })
    setContractOptions(
      res.items.map((c) => ({
        value: c.id,
        label: `${c.contractNo}（¥${(c.amount / 100).toLocaleString('zh-CN')}）`,
      })),
    )
  }

  const openCreate = () => {
    form.resetFields()
    setPlans([])
    void loadCustomers()
    void loadContracts()
    setModalOpen(true)
  }

  const openAddPlan = () => {
    planForm.resetFields()
    setPlanModalOpen(true)
  }

  const onAddPlan = async () => {
    const values = await planForm.validateFields()
    setPlans((prev) => [
      ...prev,
      {
        amount: Math.round(values.amount * 100),
        dueDate: values.dueDate!.format('YYYY-MM-DD'),
        description: values.description,
      },
    ])
    setPlanModalOpen(false)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: OrderPayload = {
      title: values.title.trim(),
      customerId: values.customerId,
      contractId: values.contractId,
      amount: values.amount === undefined ? undefined : Math.round(values.amount * 100),
      description: values.description,
      plans,
    }
    setSaving(true)
    try {
      await createOrder(payload)
      message.success(t('pages.order.list.msgCreated'))
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Order) => {
    try {
      await deleteOrder(row.id)
      message.success(t('pages.order.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const planColumns = [
    {
      title: t('pages.order.list.colAmount'),
      dataIndex: 'amount',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    { title: t('pages.order.list.colPlanDate'), dataIndex: 'dueDate' },
    { title: t('pages.order.list.colDesc'), dataIndex: 'description', render: (v?: string) => v ?? '-' },
    {
      title: '',
      key: 'actions',
      width: 60,
      render: (_: unknown, row: PlanItemPayload) => (
        <a style={{ color: '#ff4d4f' }} onClick={() => setPlans((prev) => prev.filter((p) => p !== row))}>
          <DeleteOutlined />
        </a>
      ),
    },
  ]

  const planSum = plans.reduce((s, p) => s + p.amount, 0)

  const columns: ProColumns<Order>[] = [
    {
      title: t('pages.order.list.colOrderNo'),
      dataIndex: 'orderNo',
      render: (_, row) => <Link to={`/orders/${row.id}`}>{row.orderNo}</Link>,
    },
    { title: t('pages.order.list.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.order.list.colCustomer'),
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    {
      title: t('pages.order.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.orderStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.orderStatus, code) },
        ]),
      ),
      render: (_, row) => (
        <Tag color={ORDER_STATUS_COLORS[row.status]}>
          {labelOf(t, ENUM_KEYS.orderStatus, row.status)}
        </Tag>
      ),
    },
    {
      title: t('pages.order.list.colAmount'),
      dataIndex: 'amount',
      search: false,
      render: (_, row) => (row.amount / 100).toLocaleString('zh-CN'),
    },
    {
      title: t('pages.order.list.colPaid'),
      dataIndex: 'paidAmount',
      search: false,
      render: (_, row) => ((row.paidAmount ?? 0) / 100).toLocaleString('zh-CN'),
    },
    {
      title: t('pages.order.list.colAction'),
      valueType: 'option',
      width: 100,
      render: (_, row) =>
        canDelete
          ? [
              <Popconfirm key="delete" title={t('pages.order.list.deleteConfirm', { name: row.title })} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> {t('pages.order.list.delete')}
                </a>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  return (
    <>
      <ProTable<Order>
        size="small"
        headerTitle={t('pages.order.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchOrders({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.order.list.create')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.order.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={720}
      >
        <Form
          form={form}
          name="orderForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 4 个成对字段进栅格；「描述」与下面的付款计划块（标题 + 明细表 + 合计）是整行独占，
              按使用纪律第 1 条留在栅格之外。原先的 `<Row gutter={16}>` + 4 个 `<Col span={12}>`
              + 2 个 `<Col span={24}>` 是**写死两列、无任何断点**的。 */}
          <FormGrid>
            <Form.Item name="title" label={t('pages.order.list.formTitle')} rules={[{ required: true, message: t('pages.order.list.msgTitleRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="customerId" label={t('pages.order.list.colCustomer')} rules={[{ required: true, message: t('pages.order.list.msgCustomerRequired') }]}>
              <Select
                showSearch
                placeholder={t('pages.order.list.phCustomer')}
                options={customerOptions}
                filterOption={false}
                onSearch={(kw) => void loadCustomers(kw)}
              />
            </Form.Item>
            <Form.Item name="contractId" label={t('pages.order.list.formContract')}>
              <Select allowClear placeholder={t('pages.order.list.phQuote')} options={contractOptions} />
            </Form.Item>
            <Form.Item name="amount" label={t('pages.order.list.formAmount')}>
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder={t('pages.order.list.phAmount')} />
            </Form.Item>
          </FormGrid>
          <Form.Item name="description" label={t('pages.order.list.colDesc')}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ fontWeight: 600 }}>{t('pages.order.list.paymentPlan')}</span>
            <Button size="small" icon={<PlusOutlined />} onClick={openAddPlan}>
              {t('pages.order.list.addPlan')}
            </Button>
          </div>
          <Table<PlanItemPayload>
            rowKey={(row, idx) => `${row.dueDate}-${idx}`}
            size="small"
            dataSource={plans}
            columns={planColumns as never}
            pagination={false}
            locale={{ emptyText: t('pages.order.list.planEmpty') }}
          />
          {plans.length > 0 && (
            <div style={{ textAlign: 'right', marginTop: 8, fontWeight: 600 }}>
              {t('pages.order.list.planSumPrefix')}
              {(planSum / 100).toLocaleString('zh-CN')}
            </div>
          )}
        </Form>
      </Modal>

      <Modal
        title={t('pages.order.list.addPlanModal')}
        open={planModalOpen}
        onOk={() => void onAddPlan()}
        onCancel={() => setPlanModalOpen(false)}
        okText={t('pages.order.list.addPlanOk')}
        destroyOnClose
        width={480}
      >
        <Form form={planForm} name="orderPlanForm" layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="amount" label={t('pages.order.list.planAmount')} rules={[{ required: true, message: t('pages.order.list.msgAmountRequired') }]}>
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="dueDate" label={t('pages.order.list.planDueDate')} rules={[{ required: true, message: t('pages.order.list.msgDateRequired') }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="description" label={t('pages.order.list.planDesc')}>
              <Input placeholder={t('pages.order.list.phPlan')} />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
    </>
  )
}
