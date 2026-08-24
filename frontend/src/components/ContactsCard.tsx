import { useEffect, useState } from 'react'
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Table, Tag } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createContact,
  deleteContact,
  fetchContacts,
  updateContact,
  type ContactPayload,
} from '../services/contactService'
import { extractErrorMessage } from '../services/apiClient'
import {
  ROLE_COLORS,
  ROLE_LABELS,
  type Contact,
  type ContactRole,
} from '../types/contact'

interface Props {
  customerId: number
}

interface FormValues {
  name: string
  title?: string
  phone?: string
  email?: string
  role?: ContactRole
  remark?: string
}

/** 客户详情联系人卡片：列表 + 新增/编辑/删除（005 T015）。 */
export default function ContactsCard({ customerId }: Props) {
  const { message } = App.useApp()
  const [items, setItems] = useState<Contact[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Contact | null>(null)
  const [form] = Form.useForm<FormValues>()

  const load = async () => {
    setLoading(true)
    try {
      const res = await fetchContacts({ customerId, page: 1, pageSize: 50 })
      setItems(res.items)
    } catch (err) {
      message.error(extractErrorMessage(err, '加载联系人失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Contact) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      title: row.title,
      phone: row.phone,
      email: row.email,
      role: row.role as ContactRole,
      remark: row.remark,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ContactPayload = {
      customerId,
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
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Contact) => {
    try {
      await deleteContact(row.id)
      message.success('已删除（逻辑删除）')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns = [
    { title: '姓名', dataIndex: 'name' },
    { title: '职位', dataIndex: 'title', render: (v?: string) => v ?? '-' },
    { title: '电话', dataIndex: 'phone', render: (v?: string) => v ?? '-' },
    { title: '邮箱', dataIndex: 'email', render: (v?: string) => v ?? '-' },
    {
      title: '角色',
      dataIndex: 'role',
      render: (role: ContactRole) => (
        <Tag color={ROLE_COLORS[role]}>{ROLE_LABELS[role] ?? role}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, row: Contact) => [
        <a key="edit" onClick={() => openEdit(row)}>
          <EditOutlined /> 编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除联系人「${row.name}」吗？`}
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
    <Card
      title="联系人"
      style={{ marginBottom: 16, borderRadius: 10 }}
      headStyle={{ borderBottom: '1px solid #f0f0f0' }}
      styles={{ body: { padding: 0 } }}
      extra={
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreate}>
          新增联系人
        </Button>
      }
    >
      <Table<Contact>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={items}
        columns={columns as never}
        pagination={false}
        locale={{ emptyText: '暂无联系人' }}
      />

      <Modal
        title={editing ? '编辑联系人' : '新增联系人'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={560}
      >
        <Form form={form} name="contactCardForm" layout="vertical">            <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]} style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="title" label="职位" style={{ flex: 1 }}>
              <Input />
            </Form.Item>            <Form.Item name="phone" label="电话" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
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
    </Card>
  )
}
