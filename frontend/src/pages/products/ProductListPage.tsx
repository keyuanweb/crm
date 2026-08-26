import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createProduct,
  deleteProduct,
  fetchProducts,
  updateProduct,
  type ProductPayload,
} from '../../services/productService'
import {
  deleteProductPrice,
  fetchCurrencies,
  fetchProductPrices,
  setProductPrice,
} from '../../services/currencyService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { Product } from '../../types/product'

interface FormValues {
  code: string
  name: string
  spec?: string
  unit?: string
  standardPrice: number
  status?: 'ACTIVE' | 'INACTIVE'
}

interface PriceRow {
  currencyCode: string
  price: number
  original?: string // 原始币种（判断新增/删除）
}

export default function ProductListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  // 多币种价格（057 集成）
  const [currencyOptions, setCurrencyOptions] = useState<{ value: string; label: string }[]>([])
  const [priceRows, setPriceRows] = useState<PriceRow[]>([])
  const originalPricesRef = useRef<string[]>([])

  const reload = () => actionRef.current?.reload()

  // 加载非基准币种选项
  const loadCurrencies = async () => {
    try {
      const list = await fetchCurrencies()
      setCurrencyOptions(list.filter((c) => !c.isBase).map((c) => ({ value: c.code, label: `${c.code}（${c.name}）` })))
    } catch {
      setCurrencyOptions([])
    }
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ status: 'ACTIVE' })
    setPriceRows([])
    setModalOpen(true)
    void loadCurrencies()
  }

  const openEdit = async (row: Product) => {
    setEditing(row)
    form.setFieldsValue({
      code: row.code,
      name: row.name,
      spec: row.spec,
      unit: row.unit,
      standardPrice: row.standardPrice / 100,
      status: row.status,
    })
    setPriceRows([])
    setModalOpen(true)
    void loadCurrencies()
    // 加载该产品已配置的多币种价
    try {
      const view = await fetchProductPrices(row.id)
      const configured = view.prices.filter((p) => p.configured)
      originalPricesRef.current = configured.map((p) => p.currencyCode)
      setPriceRows(
        configured.map((p) => ({ currencyCode: p.currencyCode, price: p.price / 100 })),
      )
    } catch {
      originalPricesRef.current = []
      // 价格加载失败不阻塞编辑
    }
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ProductPayload = {
      code: values.code.trim(),
      name: values.name.trim(),
      spec: values.spec?.trim(),
      unit: values.unit?.trim(),
      standardPrice: Math.round((values.standardPrice ?? 0) * 100),
      status: values.status,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateProduct(editing.id, { ...payload, version: editing.version })
        // 同步多币种价格：保存所有保留行（新增/更新），删除被移除的币种
        const keptCodes = priceRows.filter((r) => r.currencyCode)
        for (const r of keptCodes) {
          await setProductPrice(editing.id, r.currencyCode, Math.round((r.price ?? 0) * 100))
        }
        const removedCodes = originalPricesRef.current.filter((c) => !keptCodes.some((k) => k.currencyCode === c))
        for (const c of removedCodes) {
          await deleteProductPrice(editing.id, c)
        }
        message.success(t('pages.product.list.msgSaved'))
      } else {
        await createProduct(payload)
        message.success(t('pages.product.list.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Product) => {
    try {
      await deleteProduct(row.id)
      message.success(t('pages.product.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const columns: ProColumns<Product>[] = [
    { title: t('pages.product.list.colCode'), dataIndex: 'code' },
    { title: t('pages.product.list.colName'), dataIndex: 'name' },
    { title: t('pages.product.list.colSpec'), dataIndex: 'spec', search: false, render: (_, row) => row.spec ?? '-' },
    { title: t('pages.product.list.colUnit'), dataIndex: 'unit', search: false, render: (_, row) => row.unit ?? '-' },
    {
      title: t('pages.product.list.colPrice'),
      dataIndex: 'standardPrice',
      search: false,
      render: (_, row) => (row.standardPrice / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: t('pages.product.list.colStatus'),
      dataIndex: 'status',
      valueEnum: {
        ACTIVE: { text: t('common.status.active') },
        INACTIVE: { text: t('common.status.inactive') },
      },
      render: (_, row) =>
        row.status === 'ACTIVE' ? <Tag color="green">{t('common.status.active')}</Tag> : <Tag>{t('common.status.inactive')}</Tag>,
    },
    {
      title: t('pages.product.list.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) =>
        isAdmin
          ? [
              <a key="edit" onClick={() => void openEdit(row)}>
                <EditOutlined /> {t('pages.product.list.edit')}
              </a>,
              <Popconfirm key="delete" title={t('pages.product.list.deleteConfirm', { name: row.name })} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> {t('pages.product.list.delete')}
                </a>
              </Popconfirm>,
            ]
          : [],
    },
  ]

  const updatePriceRow = (index: number, patch: Partial<PriceRow>) => {
    setPriceRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  return (
    <>
      <ProTable<Product>
        size="small"
        headerTitle={t('pages.product.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchProducts({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.product.list.create')}
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? t('pages.product.list.editModal') : t('pages.product.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="productForm"
          layout="horizontal"
          labelCol={{ flex: '110px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="code" label={t('pages.product.list.formCode')} rules={[{ required: true, message: t('pages.product.list.msgCodeRequired') }]}>
                <Input placeholder={t('pages.product.list.formCodePlaceholder')} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="name" label={t('pages.product.list.formName')} rules={[{ required: true, message: t('pages.product.list.msgNameRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="spec" label={t('pages.product.list.formSpec')}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="unit" label={t('pages.product.list.formUnit')}>
                <Input placeholder={t('pages.product.list.formUnitPlaceholder')} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="standardPrice"
                label={t('pages.product.list.formPrice')}
                rules={[{ required: true, message: t('pages.product.list.msgPriceRequired') }]}
              >
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="status" label={t('pages.product.list.formStatus')}>
                <Select
                  options={[
                    { value: 'ACTIVE', label: t('common.status.active') },
                    { value: 'INACTIVE', label: t('common.status.inactive') },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        {/* 057：多币种价格（仅编辑模式） */}
        {editing && (
          <>
            <Divider style={{ margin: '12px 0' }} />
            <Typography.Text strong style={{ fontSize: 13 }}>
              {t('pages.product.list.multiPriceTitle')}
            </Typography.Text>
            <div style={{ marginTop: 10 }}>
              {priceRows.map((row, idx) => (
                <Row key={idx} gutter={8} style={{ marginBottom: 8 }} align="middle">
                  <Col xs={24} sm={9}>
                    <Select
                      style={{ width: '100%' }}
                      placeholder="Currency"
                      value={row.currencyCode || undefined}
                      options={currencyOptions}
                      onChange={(v) => updatePriceRow(idx, { currencyCode: v })}
                    />
                  </Col>
                  <Col xs={24} sm={9}>
                    <InputNumber
                      style={{ width: '100%' }}
                      min={0}
                      precision={2}
                      placeholder="Price (CNY)"
                      value={row.price}
                      onChange={(v) => updatePriceRow(idx, { price: v ?? 0 })}
                    />
                  </Col>
                  <Col xs={24} sm={6}>
                    <Button
                      danger
                      type="text"
                      icon={<DeleteOutlined />}
                      onClick={() => setPriceRows((prev) => prev.filter((_, i) => i !== idx))}
                    >
                      {t('pages.product.list.removePrice')}
                    </Button>
                  </Col>
                </Row>
              ))}
              <Button
                type="dashed"
                icon={<PlusOutlined />}
                block
                onClick={() => setPriceRows((prev) => [...prev, { currencyCode: '', price: 0 }])}
              >
                {t('pages.product.list.addPrice')}
              </Button>
              {priceRows.length === 0 && (
                <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
                  {t('pages.product.list.multiPriceEmpty')}
                </Typography.Text>
              )}
            </div>
          </>
        )}
      </Modal>
    </>
  )
}
