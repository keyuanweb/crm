import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  Table,
  Typography,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { ArrowLeftOutlined, ReloadOutlined, ShoppingCartOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchOrder, recordPayment } from '../../services/orderService'
import { extractErrorMessage } from '../../services/apiClient'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import type { PaymentPlanItem } from '../../types/order'
import { StatusTag, AmountDisplay, StatCard } from '../../components/ui'

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
  const { t } = useTranslation()

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
      message.success(t('pages.orderDetail.msgPaymentRecorded'))
      setPaymentOpen(false)
      invalidate()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.orderDetail.msgPaymentRecordFailed')))
    }
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title={t('pages.orderDetail.labelOrderNotFound')}
        extra={
          <Link to="/orders">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              {t('pages.orderDetail.btnBackToOrders')}
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status
  const paidRatio = data.amount > 0 ? (data.paidAmount ?? 0) / data.amount : 0

  // 状态 Tag
  const renderStatus = (status?: string) => {
    if (!status) return <StatusTag>-</StatusTag>
    const type: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
      PENDING: 'warning',
      PARTIAL: 'info',
      PAID: 'success',
      CANCELLED: 'default',
    }
    return <StatusTag type={type[status] ?? 'default'}>{labelOf(t, ENUM_KEYS.orderStatus, status)}</StatusTag>
  }

  const planColumns = [
    { title: t('pages.orderDetail.colSeqNo'), dataIndex: 'seqNo', width: 60, render: (v: number) => t('pages.orderDetail.seqNo', { seq: v }) },
    {
      title: t('pages.orderDetail.colReceivable'),
      dataIndex: 'amount',
      render: (v: number) => <AmountDisplay value={v} showDecimals={false} />,
    },
    {
      title: t('pages.orderDetail.colReceived'),
      dataIndex: 'receivedAmount',
      render: (v: number) => <AmountDisplay value={v} showDecimals={false} />,
    },
    {
      title: t('pages.orderDetail.colUnpaid'),
      dataIndex: 'unpaidAmount',
      render: (v: number) => (
        <Typography.Text type={v > 0 ? 'danger' : undefined}>
          <AmountDisplay value={v} showDecimals={false} />
        </Typography.Text>
      ),
    },
    {
      title: t('pages.orderDetail.colDueDate'),
      dataIndex: 'dueDate',
    },
    { title: t('pages.orderDetail.labelDescription'), dataIndex: 'description', render: (v?: string) => v ?? '-' },
    {
      title: t('pages.orderDetail.colReminder'),
      dataIndex: 'reminderStatus',
      width: 90,
      render: (reminder: string, row: PaymentPlanItem) => {
        const label =
          reminder === 'OVERDUE' && row.overdueDays
            ? t('pages.orderDetail.overdueDays', { days: row.overdueDays })
            : labelOf(t, ENUM_KEYS.orderReminder, reminder)
        const type: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
          NORMAL: 'success',
          DUE_SOON: 'warning',
          OVERDUE: 'danger',
        }
        return <StatusTag type={type[reminder] ?? 'default'}>{label}</StatusTag>
      },
    },
    {
      title: t('pages.orderDetail.colActions'),
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
            {t('pages.orderDetail.btnRecordPayment')}
          </a>
        ) : null,
    },
  ]

  const recordColumns = [
    {
      title: t('pages.orderDetail.colRecordAmount'),
      dataIndex: 'amount',
      render: (v: number) => <AmountDisplay value={v} showDecimals={false} />,
    },
    { title: t('pages.orderDetail.colRecordDate'), dataIndex: 'paidAt' },
    {
      title: t('pages.orderDetail.colRecordMethod'),
      dataIndex: 'method',
      render: (v: string) => labelOf(t, ENUM_KEYS.paymentMethod, v),
    },
    {
      title: t('pages.orderDetail.colRecordCreatedAt'),
      dataIndex: 'createdAt',
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
    },
  ]

  // 统计卡片数据
  const statsCards = [
    {
      value: `¥${(data.amount / 100).toLocaleString('zh-CN')}`,
      label: t('pages.orderDetail.labelOrderAmount'),
      icon: <ShoppingCartOutlined />,
    },
    {
      value: `¥${((data.paidAmount ?? 0) / 100).toLocaleString('zh-CN')}`,
      label: t('pages.orderDetail.labelPaidAmount'),
      icon: <CheckCircleOutlined />,
    },
    {
      value: `${Math.round(paidRatio * 100)}%`,
      label: t('pages.orderDetail.labelPaymentProgress'),
      icon: <ClockCircleOutlined />,
    },
  ]

  return (
    <div>
      {/* 返回按钮 */}
      <Link to="/orders" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }}>
          {t('pages.orderDetail.btnBackToOrders')}
        </Button>
      </Link>

      {/* 顶部信息栏 */}
      <Card
        bordered={false}
        style={{
          borderRadius: 'var(--radius-lg)',
          marginBottom: 20,
          background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-bg-card) 100%)',
          border: '1px solid var(--color-border-light)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, minWidth: 0 }}>
            {/* 订单图标 */}
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: 'var(--radius-lg)',
                background: 'var(--color-primary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 24,
                flexShrink: 0,
              }}
            >
              🛒
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {data.orderNo} - {data.title}
                </Typography.Title>
                <StatusTag type={status === 'PAID' ? 'success' : status === 'PARTIAL' ? 'info' : 'warning'}>
                  {labelOf(t, ENUM_KEYS.orderStatus, status)}
                </StatusTag>
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                {t('pages.orderDetail.labelCreatedAt')}{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
              </Typography.Text>
            </div>
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
            {t('pages.orderDetail.btnRefresh')}
          </Button>
        </div>
      </Card>

      {/* 统计卡片行 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 20 }}>
        {statsCards.map((card, index) => (
          <StatCard key={index} value={card.value} label={card.label} icon={card.icon} />
        ))}
      </div>

      {/* 付款进度条 */}
      <Card bordered={false} style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{ fontWeight: 600, minWidth: 100 }}>{t('pages.orderDetail.labelPaymentProgress')}</span>
          <Progress
            percent={Math.round(paidRatio * 100)}
            strokeColor={paidRatio >= 1 ? 'var(--color-success)' : 'var(--color-info)'}
            style={{ flex: 1 }}
          />
          <span style={{ fontWeight: 600, color: paidRatio >= 1 ? 'var(--color-success)' : 'var(--color-info)' }}>
            {Math.round(paidRatio * 100)}%
          </span>
        </div>
      </Card>

      {/* 基本信息 */}
      <Card
        title={t('pages.orderDetail.cardBasicInfo')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}
      >
        <Descriptions
          column={{ xs: 1, sm: 2, md: 3 }}
          bordered
          size="small"
          layout="horizontal"
        >
          <Descriptions.Item label={t('pages.orderDetail.labelStatus')}>
            {renderStatus(status)}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.orderDetail.labelCustomer')}>
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.orderDetail.labelContract')}>
            {data.contractId ? <Link to={`/contracts/${data.contractId}`}>{data.contractId}</Link> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.orderDetail.labelDescription')} span={3}>
            {data.description ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 收款计划 */}
      <Card
        title={t('pages.orderDetail.cardReceivable')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}
      >
        <Table<PaymentPlanItem>
          rowKey="id"
          size="middle"
          dataSource={data.plans ?? []}
          columns={planColumns as never}
          pagination={false}
          locale={{ emptyText: t('pages.orderDetail.emptyPaymentPlan') }}
          bordered
        />
      </Card>

      {/* 付款记录 */}
      <Card
        title={t('pages.orderDetail.cardPaymentRecord')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)' }}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={data.payments ?? []}
          columns={recordColumns as never}
          pagination={false}
          locale={{ emptyText: t('pages.orderDetail.emptyPaymentRecord') }}
          bordered
        />
      </Card>

      {/* 付款记录弹窗 */}
      <Modal
        title={t('pages.orderDetail.modalTitleRecordPayment')}
        open={paymentOpen}
        onOk={() => void onRecordPayment()}
        onCancel={() => setPaymentOpen(false)}
        okText={t('pages.orderDetail.btnConfirm')}
        destroyOnClose
        styles={{ body: { padding: '20px 24px' } }}
      >
        <Form form={paymentForm} name="paymentForm" layout="vertical">
          <Form.Item name="planId" label={t('pages.orderDetail.formLabelPlan')} rules={[{ required: true, message: t('pages.orderDetail.formMessageSelectPlan') }]}>
            <Select
              options={(data.plans ?? [])
                .filter((p) => p.status !== 'PAID')
                .map((p) => ({
                  value: p.id,
                  label: t('pages.orderDetail.planOptionLabel', {
                    seqNo: t('pages.orderDetail.seqNo', { seq: p.seqNo }),
                    receivable: (p.amount / 100).toLocaleString('zh-CN'),
                    unpaid: (p.unpaidAmount / 100).toLocaleString('zh-CN'),
                  }),
                }))}
            />
          </Form.Item>
          <Form.Item
            name="amount"
            label={t('pages.orderDetail.formLabelAmount')}
            rules={[{ required: true, message: t('pages.orderDetail.formMessageAmount') }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="paidAt"
            label={t('pages.orderDetail.formLabelDate')}
            rules={[{ required: true, message: t('pages.orderDetail.formMessageDate') }]}
            style={{ flex: 1 }}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="method" label={t('pages.orderDetail.formLabelMethod')} rules={[{ required: true, message: t('pages.orderDetail.formMessageMethod') }]}>
            <Select
              options={Object.keys(ENUM_KEYS.paymentMethod).map((value) => ({
                value,
                label: labelOf(t, ENUM_KEYS.paymentMethod, value),
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
