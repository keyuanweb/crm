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
  Popconfirm,
  Row,
  Select,
  Tag,
} from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createProduct,
  deleteProduct,
  fetchProducts,
  updateProduct,
  type ProductPayload,
} from '../../services/productService'
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

export default function ProductListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Product) => {
    setEditing(row)
    form.setFieldsValue({
      code: row.code,
      name: row.name,
      spec: row.spec,
      unit: row.unit,
      standardPrice: row.standardPrice / 100,
      status: row.status,
    })
    setModalOpen(true)
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
      render: (_, row) => (row.standardPrice / 100).toLocaleString('zh-CN'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
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
              <a key="edit" onClick={() => openEdit(row)}>
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
        width={560}
      >
        <Form
          form={form}
          name="productForm"
          layout="horizontal"
          labelCol={{ flex: '110px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
                <Input placeholder="如 CRM-STD" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="spec" label="规格">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="unit" label="单位">
                <Input placeholder="个/套/月" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="standardPrice"
                label="标准售价（元）"
                rules={[{ required: true, message: '请输入标准售价' }]}
              >
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
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
      </Modal>
    </>
  )
}
