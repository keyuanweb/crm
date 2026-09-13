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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { FormGrid, FormModal, useFormMetrics } from '../../components/ui'
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
  const [editing, setEditing] = useState<Product | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 标签宽度与栅格下限的唯一来源（中文 96 / 英文 112），取代此前写死的 '110px'
  // ——那是全库 5 种 labelCol 定宽里第二宽的一个。
  const metrics = useFormMetrics()
  // 按权限码判断（1.5 批 3 起 ProductController 的增删改挂 product:*，此前是 hasRole('ADMIN')）。
  // 三个码的授予范围不同：create / delete 仅 ADMIN + MARKETING_MANAGER，update 另有销售角色
  // （V83 授出）——所以销售能看到「编辑」却看不到「新建/删除」，这是矩阵的答案，不是界面漏改。
  const can = usePerms([PERMS.productCreate, PERMS.productUpdate, PERMS.productDelete])
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
    // 校验失败的 rejection 就地吃掉：antd 已把错误显示在字段下方，再弹一条 message
    // 只会重复；而放它逃出去会让 FormModal 的 handleOk 产生一个无人接管的 promise
    // rejection（FormModal **刻意不吞异常**，见其文件头）。
    // 原先的 `onOk={() => void onSave()}` 同样是裸调用，只是那时没人注意到这个 rejection。
    const values = await form.validateFields().catch(() => undefined)
    if (!values) return
    const payload: ProductPayload = {
      code: values.code.trim(),
      name: values.name.trim(),
      spec: values.spec?.trim(),
      unit: values.unit?.trim(),
      standardPrice: Math.round((values.standardPrice ?? 0) * 100),
      status: values.status,
    }
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
      render: (_, row) => [
        can[PERMS.productUpdate] && (
          <a key="edit" onClick={() => void openEdit(row)}>
            <EditOutlined /> {t('pages.product.list.edit')}
          </a>
        ),
        can[PERMS.productDelete] && (
          <Popconfirm key="delete" title={t('pages.product.list.deleteConfirm', { name: row.name })} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> {t('pages.product.list.delete')}
            </a>
          </Popconfirm>
        ),
      ],
    },
  ]

  const updatePriceRow = (index: number, patch: Partial<PriceRow>) => {
    setPriceRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  return (
    <div className="page-stack">
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
          can[PERMS.productCreate]
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.product.list.create')}
                </Button>,
              ]
            : []
        }
      />

      <FormModal
        size="md"
        title={editing ? t('pages.product.list.editModal') : t('pages.product.list.createModal')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        onSubmit={onSave}
      >
        <Form
          form={form}
          name="productForm"
          layout="horizontal"
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 6 个字段全是成对的短输入，整组进栅格。本页此前用 `<Col xs={24} sm={12}>`
              手写响应式（`:260-289`，全库仅有的 6 个响应式表单 Col），`FormGrid` 的
              auto-fit 在 md 档 640px 里算出的可用宽 592 ≥ 2 × 256 = 512 ⇒ 同样是两列。
              差异只在 576–591px 视口这一带：Col 版按 `sm` 断点排两列（每列控件约 154px，
              已低于 MIN_FIELD_WIDTH=160），auto-fit 版认为不够宽而**退成一列**——
              即栅格比断点更收敛，不是回退（见 research.md §13.2）。 */}
          <FormGrid>
            <Form.Item name="code" label={t('pages.product.list.formCode')} rules={[{ required: true, message: t('pages.product.list.msgCodeRequired') }]}>
              <Input placeholder={t('pages.product.list.formCodePlaceholder')} />
            </Form.Item>
            <Form.Item name="name" label={t('pages.product.list.formName')} rules={[{ required: true, message: t('pages.product.list.msgNameRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="spec" label={t('pages.product.list.formSpec')}>
              <Input />
            </Form.Item>
            <Form.Item name="unit" label={t('pages.product.list.formUnit')}>
              <Input placeholder={t('pages.product.list.formUnitPlaceholder')} />
            </Form.Item>
            <Form.Item
              name="standardPrice"
              label={t('pages.product.list.formPrice')}
              rules={[{ required: true, message: t('pages.product.list.msgPriceRequired') }]}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label={t('pages.product.list.formStatus')}>
              <Select
                options={[
                  { value: 'ACTIVE', label: t('common.status.active') },
                  { value: 'INACTIVE', label: t('common.status.inactive') },
                ]}
              />
            </Form.Item>
          </FormGrid>
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
                      placeholder={t('pages.product.list.priceCurrencyPlaceholder')}
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
      </FormModal>
    </div>
  )
}
