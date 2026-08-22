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
  Select,
  Space,
  Tag,
} from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createContact,
  deleteContact,
  fetchContacts,
  updateContact,
  type ContactPayload,
} from '../../services/contactService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { ROLE_COLORS, ROLE_LABELS, type Contact, type ContactRole } from '../../types/contact'

interface FormValues {
  customerId: number
  name: string
  title?: string
  phone?: string
  email?: string
  role?: ContactRole
  remark?: string
}

export default function ContactListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Contact | null>(null)
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const loadCustomers = async (keyword?: string) => {
    const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
    setCustomerOptions(res.items.map((c) => ({ value: c.id, label: c.company || c.name })))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    void loadCustomers()
    setModalOpen(true)
  }

  const openEdit = (row: Contact) => {
    setEditing(row)
    form.setFieldsValue({
      customerId: row.customerId,
      name: row.name,
      title: row.title,
      phone: row.phone,
      email: row.email,
      role: row.role as ContactRole,
      remark: row.remark,
    })
    void loadCustomers()
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ContactPayload = {
      customerId: values.customerId,
      name: values.name,
      title: values.title,
      phone: values.phone,
      email: values.email,
      role: values.role,
      remark: values.remark,
    }
    try {
      if (editing) {
        await updateContact(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createContact(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Contact) => {
    try {
      await deleteContact(row.id)
      message.success('已删除（逻辑删除）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<Contact>[] = [
    {
      title: '姓名',
      dataIndex: 'name',
      render: (_, row) => <Link to={`/customers/${row.customerId}`}>{row.name}</Link>,
    },
    {
      title: '所属客户',
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    { title: '职位', dataIndex: 'title', search: false },
    { title: '电话', dataIndex: 'phone', search: false },
    { title: '邮箱', dataIndex: 'email', search: false },
    {
      title: '角色',
      dataIndex: 'role',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ROLE_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => (
        <Tag color={ROLE_COLORS[row.role]}>{ROLE_LABELS[row.role] ?? row.role}</Tag>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          <EditOutlined /> 编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除联系人「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>
            <DeleteOutlined /> 删除
          </a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<Contact>
        headerTitle="联系人管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchContacts({
            keyword: params.keyword,
            customerId: params.customerId as number | undefined,
            role: params.role as string | undefined,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增联系人
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑联系人' : '新增联系人'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form form={form} name="contactForm" layout="vertical">
          <Form.Item
            name="customerId"
            label="所属客户"
            rules={[{ required: true, message: '请选择所属客户' }]}
          >
            <Select
              showSearch
              placeholder="搜索并选择客户"
              options={customerOptions}
              filterOption={false}
              onSearch={(kw) => void loadCustomers(kw)}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="title" label="职位" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item name="phone" label="电话" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="role" label="角色">
            <Select
              allowClear
              placeholder="默认：其他"
              options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
