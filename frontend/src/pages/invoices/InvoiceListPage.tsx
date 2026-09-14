import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
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
import { FormGrid, FormModal, StatCard, useFormMetrics } from '../../components/ui'
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
  const [orderOptions, setOrderOptions] = useState<{ label: string; value: number }[]>([])
  const [voidRow, setVoidRow] = useState<Invoice | null>(null)
  const [voidReason, setVoidReason] = useState('')
  const [stats, setStats] = useState<{ totalInvoiceAmount: number; totalOrderAmount: number; invoiceRate: number }>({
    totalInvoiceAmount: 0,
    totalOrderAmount: 0,
    invoiceRate: 0,
  })
  const [form] = Form.useForm<{ orderId: number; title: string; taxNo?: string; amount: number; invoiceType: string }>()
  // 标签宽度与栅格下限的唯一来源（中文 96 / 英文 112），取代此前写死的 '90px'。
  const metrics = useFormMetrics()
  // 作废发票走 POST /invoices/{id}/void，InvoiceController 上标的是 invoice:manage。
  const can = usePerms([PERMS.invoiceManage])

  const reload = () => {
    actionRef.current?.reload()
    void loadStats()
  }

  // `useCallback` + `useEffect` 的形态照本仓惯例（`exports/ScheduledExportListPage.tsx:60-62`）：
  // 直接写 `useEffect(() => { void loadStats() }, [])` 会让 `react-hooks/exhaustive-deps` 报缺依赖，
  // 而本仓 lint 是**零警告**的。
  const loadStats = useCallback(async () => {
    try {
      const s = await fetchInvoiceStats()
      setStats({ totalInvoiceAmount: s.totalInvoiceAmount, totalOrderAmount: s.totalOrderAmount, invoiceRate: s.invoiceRate })
    } catch {
      // 忽略
    }
  }, [])

  // 首屏加载：此前 `loadStats` 只从 `reload()` 进来，而 `reload()` 只被 `onCreate`/`onVoid` 调用，
  // 于是首次进入页面时三个统计卡片恒为 0%，只有开过票或作废过一张之后才变成真值。
  useEffect(() => {
    void loadStats()
  }, [loadStats])

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
    // 校验失败的 rejection 就地吃掉：antd 已把错误显示在字段下方，再弹一条 message
    // 只会重复；而放它逃出去会让 FormModal 的 handleOk 产生一个无人接管的 promise
    // rejection（FormModal **刻意不吞异常**，见其文件头）。
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    try {
      await createInvoice({ ...values, amount: Math.round(values.amount * 100) })
      message.success(t('pages.invoiceList.messages.created'))
      setCreateOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.invoiceList.messages.createFailed')))
    }
  }

  const onVoid = async () => {
    if (!voidRow) return
    if (!voidReason.trim()) {
      message.warning(t('pages.invoiceList.voidForm.reasonRequired'))
      return
    }
    try {
      await voidInvoice(voidRow.id, voidReason.trim())
      message.success(t('pages.invoiceList.messages.voided'))
      setVoidRow(null)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.invoiceList.messages.voidFailed')))
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
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={8}>
          <StatCard label={t('pages.invoiceList.statCards.invoiceRate')} value={`${stats.invoiceRate}%`} />
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <StatCard label={t('pages.invoiceList.statCards.totalInvoiceAmount')} value={(stats.totalInvoiceAmount / 100).toFixed(2)} />
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <StatCard label={t('pages.invoiceList.statCards.totalOrderAmount')} value={(stats.totalOrderAmount / 100).toFixed(2)} />
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
      <FormModal
        size="md"
        title={t('pages.invoiceList.modal.createInvoice')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        okText={t('pages.invoiceList.modal.createOk')}
        onSubmit={onCreate}
      >
        <Form
          form={form}
          name="invoiceForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 成对字段进栅格：md 档 640px 弹窗里可用约 592px，下限 256px ⇒ 自动两列。
              本页 5 个字段都是短输入，没有需要全宽的项；有全宽项时须放在 FormGrid **之后**
              作兄弟节点，否则会静默退化成两列里的一列（见 FormGrid 文件头的使用纪律）。 */}
          <FormGrid>
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
          </FormGrid>
        </Form>
      </FormModal>

      {/* 作废 */}
      <FormModal
        size="sm"
        title={t('pages.invoiceList.modal.voidInvoice', { invoiceNo: voidRow?.invoiceNo ?? '' })}
        open={!!voidRow}
        onCancel={() => setVoidRow(null)}
        okText={t('pages.invoiceList.modal.voidOk')}
        okButtonProps={{ danger: true }}
        onSubmit={onVoid}
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
      </FormModal>
    </div>
  )
}
