import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createInvoice,
  fetchInvoiceStats,
  fetchInvoices,
  fetchOrdersForInvoice,
  voidInvoice,
} from '../../services/invoiceService'
import { extractErrorMessage } from '../../services/apiClient'
import type { Invoice } from '../../types/invoice'

const STATUS_META: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '待开', color: 'default' },
  ISSUED: { text: '已开', color: 'green' },
  VOID: { text: '已作废', color: 'red' },
}

const TYPE_LABELS: Record<string, string> = {
  GENERAL: '普票',
  SPECIAL: '专票',
}

export default function InvoiceListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [orderOptions, setOrderOptions] = useState<{ label: string; value: number }[]>([])
  const [voidRow, setVoidRow] = useState<Invoice | null>(null)
  const [voidReason, setVoidReason] = useState('')
  const [stats, setStats] = useState<{ totalInvoiceAmount: number; totalOrderAmount: number; invoiceRate: number }>({
    totalInvoiceAmount: 0,
    totalOrderAmount: 0,
    invoiceRate: 0,
  })
  const [form] = Form.useForm<{ orderId: number; title: string; taxNo?: string; amount: number; invoiceType: string }>()

  const reload = () => {
    actionRef.current?.reload()
    void loadStats()
  }

  const loadStats = async () => {
    try {
      const s = await fetchInvoiceStats()
      setStats({ totalInvoiceAmount: s.totalInvoiceAmount, totalOrderAmount: s.totalOrderAmount, invoiceRate: s.invoiceRate })
    } catch {
      // 忽略
    }
  }

  const openCreate = async () => {
    form.resetFields()
    form.setFieldsValue({ invoiceType: 'GENERAL' })
    try {
      const orders = await fetchOrdersForInvoice()
      setOrderOptions(orders.map((o) => ({ label: `${o.orderNo}（¥${(o.amount / 100).toFixed(2)}）`, value: o.id })))
    } catch {
      setOrderOptions([])
    }
    setCreateOpen(true)
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await createInvoice({ ...values, amount: Math.round(values.amount * 100) })
      message.success('开票成功')
      setCreateOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '开票失败'))
    } finally {
      setSaving(false)
    }
  }

  const onVoid = async () => {
    if (!voidRow) return
    if (!voidReason.trim()) {
      message.warning('请填写作废原因')
      return
    }
    setSaving(true)
    try {
      await voidInvoice(voidRow.id, voidReason.trim())
      message.success('已作废')
      setVoidRow(null)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '作废失败'))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<Invoice>[] = [
    { title: '发票编号', dataIndex: 'invoiceNo' },
    { title: '订单', dataIndex: 'orderNo', width: 140, render: (_, row) => row.orderNo || '-' },
    { title: '抬头', dataIndex: 'title', ellipsis: true },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      width: 110,
      search: false,
      render: (_, row) => (row.amount / 100).toFixed(2),
    },
    {
      title: '类型',
      dataIndex: 'invoiceType',
      width: 70,
      render: (_, row) => <Tag color="blue">{TYPE_LABELS[row.invoiceType] ?? row.invoiceType}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, row) => {
        const m = STATUS_META[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={m.color}>{m.text}</Tag>
      },
    },
    {
      title: '开票时间',
      dataIndex: 'issuedAt',
      width: 140,
      search: false,
      render: (_, row) => (row.issuedAt ? row.issuedAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 90,
      render: (_, row) =>
        row.status === 'ISSUED' ? (
          <a style={{ color: '#ff4d4f' }} onClick={() => setVoidRow(row)}>
            作废
          </a>
        ) : null,
    },
  ]

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title="开票率" value={stats.invoiceRate} suffix="%" valueStyle={{ color: '#1677ff' }} />
          </div>
        </Col>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title="已开票金额（元）" value={(stats.totalInvoiceAmount / 100).toFixed(2)} valueStyle={{ color: '#3f8600' }} />
          </div>
        </Col>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title="订单总金额（元）" value={(stats.totalOrderAmount / 100).toFixed(2)} />
          </div>
        </Col>
      </Row>

      <ProTable<Invoice>
        size="small"
        headerTitle="发票管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ pageSize: 10 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchInvoices({ page: params.current ?? 1, pageSize: params.pageSize ?? 10 })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => void openCreate()}>
            开票
          </Button>,
        ]}
      />

      {/* 开票 */}
      <Modal
        title="开具发票"
        open={createOpen}
        onOk={() => void onCreate()}
        confirmLoading={saving}
        onCancel={() => setCreateOpen(false)}
        okText="开票"
        destroyOnClose
      >
        <Form form={form} name="invoiceForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="orderId" label="订单" rules={[{ required: true, message: '请选择订单' }]}>
            <Select showSearch optionFilterProp="label" placeholder="选择订单" options={orderOptions} />
          </Form.Item>
          <Form.Item name="title" label="抬头" rules={[{ required: true, message: '请输入抬头' }]}>
            <Input placeholder="开票抬头" />
          </Form.Item>
          <Form.Item name="taxNo" label="税号">
            <Input placeholder="纳税人识别号" />
          </Form.Item>
          <Form.Item name="amount" label="金额(元)" rules={[{ required: true, message: '请输入金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="invoiceType" label="类型">
            <Select
              options={[
                { value: 'GENERAL', label: '增值税普通发票' },
                { value: 'SPECIAL', label: '增值税专用发票' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 作废 */}
      <Modal
        title={`作废发票 ${voidRow?.invoiceNo ?? ''}`}
        open={!!voidRow}
        onOk={() => void onVoid()}
        confirmLoading={saving}
        onCancel={() => setVoidRow(null)}
        okText="确认作废"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>作废后释放可开票额度，该发票不可恢复。</div>
          <Input.TextArea
            rows={3}
            placeholder="作废原因（必填）"
            value={voidReason}
            onChange={(e) => setVoidReason(e.target.value)}
          />
        </Space>
      </Modal>
    </>
  )
}
