import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  InputNumber,
  Modal,
  Progress,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchOrder, recordPayment } from '../../services/orderService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  ORDER_STATUS_COLORS,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
  REMINDER_COLORS,
  REMINDER_LABELS,
  type PaymentPlanItem,
} from '../../types/order'

interface PaymentFormValues {
  planId: number
  amount: number
  paidAt?: Dayjs
  method: string
}

export default function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [paymentForm] = Form.useForm<PaymentFormValues>()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => fetchOrder(orderId),
    enabled: Number.isFinite(orderId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['order', orderId] })
    void refetch()
  }

  const onRecordPayment = async () => {
    const values = await paymentForm.validateFields()
    try {
      await recordPayment(orderId, {
        planId: values.planId,
        amount: Math.round(values.amount * 100),
        paidAt: values.paidAt!.format('YYYY-MM-DD'),
        method: values.method,
      })
      message.success('回款已登记')
      setPaymentOpen(false)
      invalidate()
    } catch (err) {
      message.error(extractErrorMessage(err, '登记失败'))
    }
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title="订单不存在或已被删除"
        extra={
          <Link to="/orders">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              返回订单列表
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status
  const paidRatio = data.amount > 0 ? (data.paidAmount ?? 0) / data.amount : 0

  const planColumns = [
    { title: '期次', dataIndex: 'seqNo', width: 60, render: (v: number) => `第 ${v} 期` },
    {
      title: '应收（元）',
      dataIndex: 'amount',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    {
      title: '已收（元）',
      dataIndex: 'receivedAmount',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    {
      title: '未收（元）',
      dataIndex: 'unpaidAmount',
      render: (v: number) => <Typography.Text type={v > 0 ? 'danger' : undefined}>{(v / 100).toLocaleString('zh-CN')}</Typography.Text>,
    },
    { title: '计划日期', dataIndex: 'dueDate' },
    { title: '说明', dataIndex: 'description', render: (v?: string) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'reminderStatus',
      width: 90,
      render: (reminder: string, row: PaymentPlanItem) => {
        const label =
          reminder === 'OVERDUE' && row.overdueDays
            ? `逾期${row.overdueDays}天`
            : REMINDER_LABELS[reminder as keyof typeof REMINDER_LABELS] ?? reminder
        return <Tag color={REMINDER_COLORS[reminder as keyof typeof REMINDER_COLORS] ?? 'default'}>{label}</Tag>
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_: unknown, row: PaymentPlanItem) =>
        row.status !== 'PAID' ? (
          <a
            onClick={() => {
              paymentForm.setFieldsValue({
                planId: row.id,
                amount: row.unpaidAmount / 100,
                method: 'TRANSFER',
              })
              setPaymentOpen(true)
            }}
          >
            登记回款
          </a>
        ) : null,
    },
  ]

  const recordColumns = [
    {
      title: '金额（元）',
      dataIndex: 'amount',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    { title: '回款日期', dataIndex: 'paidAt' },
    {
      title: '方式',
      dataIndex: 'method',
      render: (v: string) => PAYMENT_METHOD_LABELS[v] ?? v,
    },
    {
      title: '登记时间',
      dataIndex: 'createdAt',
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
    },
  ]

  return (
    <div>
      <Link to="/orders" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回订单列表
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.orderNo} - {data.title}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            创建时间：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
          </Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
          刷新
        </Button>
      </div>

      <Card title="基本信息" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="状态">
            <Tag color={ORDER_STATUS_COLORS[status]}>{ORDER_STATUS_LABELS[status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户">
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label="关联合同">
            {data.contractId ? <Link to={`/contracts/${data.contractId}`}>{data.contractId}</Link> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="订单金额（元）">
            <Typography.Text strong>¥ {(data.amount / 100).toLocaleString('zh-CN')}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="已回款（元）">
            ¥ {((data.paidAmount ?? 0) / 100).toLocaleString('zh-CN')}
          </Descriptions.Item>
          <Descriptions.Item label="回款进度">
            <Progress percent={Math.round(paidRatio * 100)} size="small" style={{ width: 160 }} />
          </Descriptions.Item>
          <Descriptions.Item label="说明" span={2}>
            {data.description ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title="应收账款台账"
        style={{ marginBottom: 16, borderRadius: 10 }}
        bodyStyle={{ padding: 0 }}
      >
        <Table<PaymentPlanItem>
          rowKey="id"
          size="middle"
          dataSource={data.plans ?? []}
          columns={planColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无回款计划' }}
        />
      </Card>

      <Card title="回款记录" style={{ borderRadius: 10 }} bodyStyle={{ padding: 0 }}>
        <Table
          rowKey="id"
          size="small"
          dataSource={data.payments ?? []}
          columns={recordColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无回款记录' }}
        />
      </Card>

      <Modal
        title="登记回款"
        open={paymentOpen}
        onOk={() => void onRecordPayment()}
        onCancel={() => setPaymentOpen(false)}
        okText="登记"
        destroyOnClose
      >
        <Form form={paymentForm} name="paymentForm" layout="vertical">
          <Form.Item name="planId" label="回款期次" rules={[{ required: true, message: '请选择期次' }]}>
            <Select
              options={(data.plans ?? [])
                .filter((p) => p.status !== 'PAID')
                .map((p) => ({
                  value: p.id,
                  label: `第 ${p.seqNo} 期（应收 ¥${(p.amount / 100).toLocaleString('zh-CN')}，未收 ¥${(p.unpaidAmount / 100).toLocaleString('zh-CN')}）`,
                }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item
              name="amount"
              label="回款金额（元）"
              rules={[{ required: true, message: '请输入回款金额' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="paidAt"
              label="回款日期"
              rules={[{ required: true, message: '请选择日期' }]}
              style={{ flex: 1 }}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="method" label="回款方式" rules={[{ required: true, message: '请选择方式' }]}>
            <Select options={Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
