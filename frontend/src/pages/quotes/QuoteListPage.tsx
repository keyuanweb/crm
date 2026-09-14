import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Table,
  Tag,
} from 'antd'
import type { Dayjs } from 'dayjs'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createQuote,
  fetchQuotes,
  type QuotePayload,
} from '../../services/quoteService'
import { fetchCustomers } from '../../services/customerService'
import { fetchProducts } from '../../services/productService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  QUOTE_STATUS_COLORS,
  type Quote,
  type QuoteItemPayload,
} from '../../types/quote'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import type { Product } from '../../types/product'

interface FormValues {
  customerId: number
  validUntil?: Dayjs
  remark?: string
}

interface LineFormValues {
  productId: number
  quantity: number
  discount: number
}

export default function QuoteListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [lineForm] = Form.useForm<LineFormValues>()
  const [lines, setLines] = useState<QuoteItemPayload[]>([])
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [productOptions, setProductOptions] = useState<Product[]>([])
  const [lineModalOpen, setLineModalOpen] = useState(false)

  const reload = () => actionRef.current?.reload()

  const loadCustomers = async (keyword?: string) => {
    const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
    setCustomerOptions(res.items.map((c) => ({ value: c.id, label: c.company || c.name })))
  }

  const loadProducts = async () => {
    const res = await fetchProducts({ status: 'ACTIVE', page: 1, pageSize: 100 })
    setProductOptions(res.items)
  }

  const openCreate = () => {
    form.resetFields()
    setLines([])
    void loadCustomers()
    void loadProducts()
    setModalOpen(true)
  }

  const openAddLine = () => {
    lineForm.resetFields()
    setLineModalOpen(true)
  }

  const onAddLine = async () => {
    const values = await lineForm.validateFields()
    const product = productOptions.find((p) => p.id === values.productId)
    if (!product) return
    setLines((prev) => [
      ...prev,
      {
        productId: values.productId,
        quantity: values.quantity,
        discount: values.discount / 100, // 前端 0-100 → 后端 0-1（实付比例）
      },
    ])
    setLineModalOpen(false)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    if (lines.length === 0) {
      message.warning(t('pages.quoteList.messages.pleaseAddProduct'))
      return
    }
    const payload: QuotePayload = {
      customerId: values.customerId,
      validUntil: values.validUntil ? values.validUntil.format('YYYY-MM-DD') : undefined,
      remark: values.remark,
      items: lines,
    }
    setSaving(true)
    try {
      await createQuote(payload)
      message.success(t('pages.quoteList.messages.created'))
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.quoteList.messages.createFailed')))
    } finally {
      setSaving(false)
    }
  }

  const lineColumns = [
    {
      title: t('pages.quoteList.form.product'),
      dataIndex: 'productId',
      render: (id: number) => productOptions.find((p) => p.id === id)?.name ?? id,
    },
    { title: t('pages.quoteList.form.quantity'), dataIndex: 'quantity' },
    {
      title: t('pages.quoteList.colDiscount'),
      dataIndex: 'discount',
      render: (v: number) => `${Math.round(v * 100)}%`,
    },
    {
      title: t('pages.quoteList.colSubtotal'),
      key: 'lineTotal',
      render: (_: unknown, row: QuoteItemPayload) => {
        const product = productOptions.find((p) => p.id === row.productId)
        const total = Math.round(((product?.standardPrice ?? 0) / 100) * row.quantity * row.discount * 100) / 100
        return total.toLocaleString('zh-CN')
      },
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      render: (_: unknown, row: QuoteItemPayload) => (
        <a
          style={{ color: '#ff4d4f' }}
          onClick={() => setLines((prev) => prev.filter((l) => l !== row))}
        >
          <DeleteOutlined />
        </a>
      ),
    },
  ]

  const totalAmount = lines.reduce((sum, line) => {
    const product = productOptions.find((p) => p.id === line.productId)
    return sum + Math.round(((product?.standardPrice ?? 0) / 100) * line.quantity * line.discount * 100) / 100
  }, 0)

  const columns: ProColumns<Quote>[] = [
    {
      title: t('pages.quoteList.colQuoteNo'),
      dataIndex: 'quoteNo',
      render: (_, row) => <Link to={`/quotes/${row.id}`}>{row.quoteNo}</Link>,
    },
    {
      title: t('pages.quoteList.colCustomer'),
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    {
      title: t('pages.quoteList.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.quoteStatus).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.quoteStatus, code) },
        ]),
      ),
      render: (_, row) => (
        <Tag color={QUOTE_STATUS_COLORS[row.status]}>
          {labelOf(t, ENUM_KEYS.quoteStatus, row.status)}
        </Tag>
      ),
    },
    {
      title: t('pages.quoteList.colTotalAmount'),
      dataIndex: 'totalAmount',
      search: false,
      render: (_, row) => (row.totalAmount / 100).toLocaleString('zh-CN'),
    },
    {
      title: t('pages.quoteList.colValidUntil'),
      dataIndex: 'validUntil',
      search: false,
      render: (_, row) => row.validUntil ?? '-',
    },
    {
      title: t('pages.quoteList.colCreatedAt'),
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
  ]

  return (
    <>
      <ProTable<Quote>
        size="small"
        headerTitle={t('pages.quoteList.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchQuotes({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.quoteList.toolbar.newQuote')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.quoteList.modal.newQuote')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.quoteList.modal.saveDraft')}
        destroyOnClose
        width={760}
      >
        <Form
          form={form}
          name="quoteForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="customerId"
                label={t('pages.quoteList.form.customer')}
                rules={[{ required: true, message: t('pages.quoteList.form.customerRequired') }]}
              >
                <Select
                  showSearch
                  placeholder={t('pages.quoteList.form.customerPlaceholder')}
                  options={customerOptions}
                  filterOption={false}
                  onSearch={(kw) => void loadCustomers(kw)}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="validUntil" label={t('pages.quoteList.form.validUntil')}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="remark" label={t('pages.quoteList.form.remark')}>
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ fontWeight: 600 }}>{t('pages.quoteList.form.productLines')}</span>
            <Button size="small" icon={<PlusOutlined />} onClick={openAddLine}>
              {t('pages.quoteList.form.addProduct')}
            </Button>
          </div>
          <Table<QuoteItemPayload>
            rowKey={(row, idx) => `${row.productId}-${idx}`}
            size="small"
            dataSource={lines}
            columns={lineColumns as never}
            pagination={false}
            locale={{ emptyText: t('pages.quoteList.form.noProducts') }}
          />
          <div style={{ textAlign: 'right', marginTop: 8, fontWeight: 600 }}>
            {t('pages.quoteList.form.total')}：¥ {totalAmount.toLocaleString('zh-CN')}
          </div>
        </Form>
      </Modal>

      <Modal
        title={t('pages.quoteList.modal.addProduct')}
        open={lineModalOpen}
        onOk={() => void onAddLine()}
        onCancel={() => setLineModalOpen(false)}
        okText={t('pages.quoteList.modal.addProductOk')}
        destroyOnClose
        width={480}
      >
        <Form form={lineForm} name="quoteLineForm" layout="vertical">
          <Form.Item
            name="productId"
            label={t('pages.quoteList.form.product')}
            rules={[{ required: true, message: t('pages.quoteList.form.productRequired') }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={productOptions.map((p) => ({
                value: p.id,
                label: `${p.name}（¥${(p.standardPrice / 100).toLocaleString('zh-CN')}）`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="quantity"
            label={t('pages.quoteList.form.quantity')}
            initialValue={1}
            rules={[{ required: true, message: t('pages.quoteList.form.quantityRequired') }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="discount"
            label={t('pages.quoteList.form.discount')}
            initialValue={100}
            rules={[{ required: true, message: t('pages.quoteList.form.discountRequired') }]}
          >
            <InputNumber min={0} max={100} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
