import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { Invoice } from '../../types/invoice'

const getSTATUS_META = (t: (key: string, params?: Record<string, unknown>) => string): Record<string, { text: string; color: string }> => ({
  DRAFT: { text: t('pages.invoiceList.status.draft'), color: 'default' },
  ISSUED: { text: t('pages.invoiceList.status.issued'), color: 'green' },
  VOID: { text: t('pages.invoiceList.status.void'), color: 'red' },
})

const getTYPE_LABELS = (t: (key: string, params?: Record<string, unknown>) => string): Record<string, string> => ({
  GENERAL: t('pages.invoiceList.type.general'),
  SPECIAL: t('pages.invoiceList.type.special'),
})

export default function InvoiceListPage() {
  const actionRef = useRef<ActionType>()
  const { t } = useTranslation()
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
  // 作废发票走 POST /invoices/{id}/void，InvoiceController 上标的是 invoice:manage。
  const can = usePerms([PERMS.invoiceManage])

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
      message.success(t('pages.invoiceList.messages.created'))
      setCreateOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.invoiceList.messages.createFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onVoid = async () => {
    if (!voidRow) return
    if (!voidReason.trim()) {
      message.warning(t('pages.invoiceList.voidForm.reasonRequired'))
      return
    }
    setSaving(true)
    try {
      await voidInvoice(voidRow.id, voidReason.trim())
      message.success(t('pages.invoiceList.messages.voided'))
      setVoidRow(null)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.invoiceList.messages.voidFailed')))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<Invoice>[] = [
    { title: t('pages.invoiceList.colInvoiceNo'), dataIndex: 'invoiceNo' },
    { title: t('pages.invoiceList.colOrder'), dataIndex: 'orderNo', width: 140, render: (_, row) => row.orderNo || '-' },
    { title: t('pages.invoiceList.colTitle'), dataIndex: 'title', ellipsis: true },
    {
      title: t('pages.invoiceList.colAmount'),
      dataIndex: 'amount',
      width: 110,
      search: false,
      render: (_, row) => (row.amount / 100).toFixed(2),
    },
    {
      title: t('pages.invoiceList.colType'),
      dataIndex: 'invoiceType',
      width: 70,
      render: (_, row) => <Tag color="blue">{getTYPE_LABELS(t)[row.invoiceType] ?? row.invoiceType}</Tag>,
    },
    {
      title: t('pages.invoiceList.colStatus'),
      dataIndex: 'status',
      width: 90,
      render: (_, row) => {
        const m = getSTATUS_META(t)[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={m.color}>{m.text}</Tag>
      },
    },
    {
      title: t('pages.invoiceList.colIssuedAt'),
      dataIndex: 'issuedAt',
      width: 140,
      search: false,
      render: (_, row) => (row.issuedAt ? row.issuedAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.invoiceList.colAction'),
      valueType: 'option',
      width: 90,
      render: (_, row) =>
        row.status === 'ISSUED' && can[PERMS.invoiceManage] ? (
          <a style={{ color: '#ff4d4f' }} onClick={() => setVoidRow(row)}>
            {t('pages.invoiceList.status.void')}
          </a>
        ) : null,
    },
  ]

  return (
    <div className="page-stack">
      <Row gutter={16}>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title={t('pages.invoiceList.statCards.invoiceRate')} value={stats.invoiceRate} suffix="%" valueStyle={{ color: '#1677ff' }} />
          </div>
        </Col>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title={t('pages.invoiceList.statCards.totalInvoiceAmount')} value={(stats.totalInvoiceAmount / 100).toFixed(2)} valueStyle={{ color: '#3f8600' }} />
          </div>
        </Col>
        <Col span={8}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title={t('pages.invoiceList.statCards.totalOrderAmount')} value={(stats.totalOrderAmount / 100).toFixed(2)} />
          </div>
        </Col>
      </Row>

      <ProTable<Invoice>
        size="small"
        headerTitle={t('pages.invoiceList.title')}
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
            {t('pages.invoiceList.toolbar.invoice')}
          </Button>,
        ]}
      />

      {/* 开票 */}
      <Modal
        title={t('pages.invoiceList.modal.createInvoice')}
        open={createOpen}
        onOk={() => void onCreate()}
        confirmLoading={saving}
        onCancel={() => setCreateOpen(false)}
        okText={t('pages.invoiceList.modal.createOk')}
        destroyOnClose
      >
        <Form form={form} name="invoiceForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="orderId" label={t('pages.invoiceList.form.order')} rules={[{ required: true, message: t('pages.invoiceList.form.orderRequired') }]}>
            <Select showSearch optionFilterProp="label" placeholder={t('pages.invoiceList.form.orderPlaceholder')} options={orderOptions} />
          </Form.Item>
          <Form.Item name="title" label={t('pages.invoiceList.form.title')} rules={[{ required: true, message: t('pages.invoiceList.form.titleRequired') }]}>
            <Input placeholder={t('pages.invoiceList.form.titlePlaceholder')} />
          </Form.Item>
          <Form.Item name="taxNo" label={t('pages.invoiceList.form.taxNo')}>
            <Input placeholder={t('pages.invoiceList.form.taxNoPlaceholder')} />
          </Form.Item>
          <Form.Item name="amount" label={t('pages.invoiceList.form.amount')} rules={[{ required: true, message: t('pages.invoiceList.form.amountRequired') }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="invoiceType" label={t('pages.invoiceList.form.invoiceType')}>
            <Select
              options={[
                { value: 'GENERAL', label: t('pages.invoiceList.type.generalFull') },
                { value: 'SPECIAL', label: t('pages.invoiceList.type.specialFull') },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 作废 */}
      <Modal
        title={t('pages.invoiceList.modal.voidInvoice', { invoiceNo: voidRow?.invoiceNo ?? '' })}
        open={!!voidRow}
        onOk={() => void onVoid()}
        confirmLoading={saving}
        onCancel={() => setVoidRow(null)}
        okText={t('pages.invoiceList.modal.voidOk')}
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>{t('pages.invoiceList.voidForm.description')}</div>
          <Input.TextArea
            rows={3}
            placeholder={t('pages.invoiceList.voidForm.reasonPlaceholder')}
            value={voidReason}
            onChange={(e) => setVoidReason(e.target.value)}
          />
        </Space>
      </Modal>
    </div>
  )
}
