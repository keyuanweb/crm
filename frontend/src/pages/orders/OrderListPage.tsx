import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
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
  Space,
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
import {
  ORDER_STATUS_COLORS,
  ORDER_STATUS_LABELS,
  type Order,
  type PlanItemPayload,
} from '../../types/order'

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
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [planForm] = Form.useForm<PlanFormValues>()
  const [plans, setPlans] = useState<PlanItemPayload[]>([])
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [contractOptions, setContractOptions] = useState<{ value: number; label: string }[]>([])
  const [planModalOpen, setPlanModalOpen] = useState(false)
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

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
    try {
      await createOrder(payload)
      message.success('已创建')
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    }
  }

  const onDelete = async (row: Order) => {
    try {
      await deleteOrder(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const planColumns = [
    {
      title: '金额（元）',
      dataIndex: 'amount',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    { title: '计划日期', dataIndex: 'dueDate' },
    { title: '说明', dataIndex: 'description', render: (v?: string) => v ?? '-' },
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
      title: '订单号',
      dataIndex: 'orderNo',
      render: (_, row) => <Link to={`/orders/${row.id}`}>{row.orderNo}</Link>,
    },
    { title: '标题', dataIndex: 'title' },
    {
      title: '客户',
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ORDER_STATUS_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => (
        <Tag color={ORDER_STATUS_COLORS[row.status]}>{ORDER_STATUS_LABELS[row.status]}</Tag>
      ),
    },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      search: false,
      render: (_, row) => (row.amount / 100).toLocaleString('zh-CN'),
    },
    {
      title: '已回款（元）',
      dataIndex: 'paidAmount',
      search: false,
      render: (_, row) => ((row.paidAmount ?? 0) / 100).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) =>
        isAdmin
          ? [
              <Popconfirm key="delete" title={`确定删除订单「${row.title}」吗？`} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> 删除
                </a>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  return (
    <>
      <ProTable<Order>
        headerTitle="订单管理"
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
            新建订单
          </Button>,
        ]}
      />

      <Modal
        title="新建订单"
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={720}
      >
        <Form form={form} name="orderForm" layout="vertical">
          <Form.Item name="title" label="订单标题" rules={[{ required: true, message: '请输入订单标题' }]}>
            <Input />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item
              name="customerId"
              label="客户"
              rules={[{ required: true, message: '请选择客户' }]}
              style={{ flex: 1 }}
            >
              <Select
                showSearch
                placeholder="搜索并选择客户"
                options={customerOptions}
                filterOption={false}
                onSearch={(kw) => void loadCustomers(kw)}
              />
            </Form.Item>
            <Form.Item name="contractId" label="关联合同（生效中）" style={{ flex: 1 }}>
              <Select allowClear placeholder="可选，选择后自动带入金额" options={contractOptions} />
            </Form.Item>
          </Space>
          <Form.Item name="amount" label="订单金额（元）">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="选择合同后自动带入" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ fontWeight: 600 }}>回款计划（可选，留空自动一期）</span>
            <Button size="small" icon={<PlusOutlined />} onClick={openAddPlan}>
              添加期次
            </Button>
          </div>
          <Table<PlanItemPayload>
            rowKey={(row, idx) => `${row.dueDate}-${idx}`}
            size="small"
            dataSource={plans}
            columns={planColumns as never}
            pagination={false}
            locale={{ emptyText: '暂无期次（将自动生成一期）' }}
          />
          {plans.length > 0 && (
            <div style={{ textAlign: 'right', marginTop: 8, fontWeight: 600 }}>
              期次合计：¥ {(planSum / 100).toLocaleString('zh-CN')}
            </div>
          )}
        </Form>
      </Modal>

      <Modal
        title="添加期次"
        open={planModalOpen}
        onOk={() => void onAddPlan()}
        onCancel={() => setPlanModalOpen(false)}
        okText="添加"
        destroyOnClose
        width={480}
      >
        <Form form={planForm} name="orderPlanForm" layout="vertical">
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item
              name="amount"
              label="金额（元）"
              rules={[{ required: true, message: '请输入金额' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="dueDate"
              label="计划回款日期"
              rules={[{ required: true, message: '请选择日期' }]}
              style={{ flex: 1 }}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="期次说明">
            <Input placeholder="如：首付/尾款" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
