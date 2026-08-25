import { useRef, useState } from 'react'
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
        message.success('已保存')
      } else {
        await createProduct(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Product) => {
    try {
      await deleteProduct(row.id)
      message.success('已删除（逻辑删除）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<Product>[] = [
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '规格', dataIndex: 'spec', search: false, render: (_, row) => row.spec ?? '-' },
    { title: '单位', dataIndex: 'unit', search: false, render: (_, row) => row.unit ?? '-' },
    {
      title: '标准售价（元）',
      dataIndex: 'standardPrice',
      search: false,
      render: (_, row) => (row.standardPrice / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2 }),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueEnum: {
        ACTIVE: { text: '启用' },
        INACTIVE: { text: '停用' },
      },
      render: (_, row) =>
        row.status === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) =>
        isAdmin
          ? [
              <a key="edit" onClick={() => void openEdit(row)}>
                <EditOutlined /> 编辑
              </a>,
              <Popconfirm key="delete" title={`确定删除产品「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
                <a style={{ color: '#ff4d4f' }}>
                  <DeleteOutlined /> 删除
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
        headerTitle="产品目录"
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
                  新增产品
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? '编辑产品' : '新增产品'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
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
              <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
                <Input placeholder="如 CRM-STD" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="spec" label="规格">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="unit" label="单位">
                <Input placeholder="个/套/月" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="standardPrice"
                label="标准售价（元）"
                rules={[{ required: true, message: '请输入标准售价' }]}
              >
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="status" label="状态">
                <Select
                  options={[
                    { value: 'ACTIVE', label: '启用' },
                    { value: 'INACTIVE', label: '停用' },
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
              多币种价格（未配置币种按汇率自动折算）
            </Typography.Text>
            <div style={{ marginTop: 10 }}>
              {priceRows.map((row, idx) => (
                <Row key={idx} gutter={8} style={{ marginBottom: 8 }} align="middle">
                  <Col xs={24} sm={9}>
                    <Select
                      style={{ width: '100%' }}
                      placeholder="币种"
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
                      placeholder="价格（元）"
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
                      移除
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
                添加币种价格
              </Button>
              {priceRows.length === 0 && (
                <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
                  暂无配置，销售端将按汇率自动折算各币种价格
                </Typography.Text>
              )}
            </div>
          </>
        )}
      </Modal>
    </>
  )
}
