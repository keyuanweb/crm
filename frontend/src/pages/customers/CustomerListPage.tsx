import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Upload,
} from 'antd'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  createCustomer,
  deleteCustomer,
  downloadTemplate,
  exportCustomers,
  fetchCustomers,
  importCustomers,
  updateCustomer,
  type CustomerPayload,
  type ImportResult,
} from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import type { Customer } from '../../types/customer'

interface FormValues {
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  remark?: string
}

export default function CustomerListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  const [form] = Form.useForm<FormValues>()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Customer) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      company: row.company,
      contactPerson: row.contactPerson,
      phone: row.phone,
      email: row.email,
      address: row.address,
      remark: row.remark,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: CustomerPayload = { name: values.name, company: values.company }
    for (const [key, value] of Object.entries(values)) {
      if (key !== 'name' && key !== 'company' && value !== undefined && value !== '') {
        payload[key as keyof CustomerPayload] = value as never
      }
    }
    try {
      if (editing) {
        await updateCustomer(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createCustomer(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Customer) => {
    try {
      await deleteCustomer(row.id)
      message.success('已删除（逻辑删除）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onImport = async (file: File) => {
    try {
      const result: ImportResult = await importCustomers(file)
      message.success(`导入完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '导入失败'))
    }
    return false
  }

  const columns: ProColumns<Customer>[] = [
    {
      title: '客户名称',
      dataIndex: 'name',
      render: (_, row) => <Link to={`/customers/${row.id}`}>{row.name}</Link>,
    },
    { title: '公司', dataIndex: 'company' },
    { title: '联系人', dataIndex: 'contactPerson', search: false },
    { title: '电话', dataIndex: 'phone', search: false },
    { title: '邮箱', dataIndex: 'email', search: false },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '启用', status: 'Success' },
        INACTIVE: { text: '停用', status: 'Default' },
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          <EditOutlined /> 编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除客户「${row.name}」吗？（逻辑删除，可恢复）`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>
            <DeleteOutlined /> 删除
          </a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Customer>
        headerTitle="客户管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchCustomers({
            keyword: params.keyword,
            status: params.status,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          ...(isAdmin
            ? [
                <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
                  <Button icon={<UploadOutlined />}>导入</Button>
                </Upload>,
                <Button key="template" icon={<DownloadOutlined />} onClick={() => void downloadTemplate()}>
                  下载模板
                </Button>,
                <Button key="export" icon={<DownloadOutlined />} onClick={() => void exportCustomers({})}>
                  导出
                </Button>,
              ]
            : []),
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增客户
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑客户' : '新增客户'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存" destroyOnClose>
        <Form form={form} name="customerForm" layout="vertical">
          <Form.Item name="name" label="客户名称" rules={[{ required: true, message: '请输入客户名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="company" label="公司" rules={[{ required: true, message: '请输入公司' }]}>
            <Input />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="contactPerson" label="联系人" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="电话" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="address" label="地址" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
