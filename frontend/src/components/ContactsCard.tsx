import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  type Contact,
  type ContactRole,
} from '../types/contact'
import { ENUM_KEYS, labelOf } from '../constants/enumLabels'

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
  const { t } = useTranslation()
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
      message.error(extractErrorMessage(err, t('pages.contactsCard.msgLoadFailed')))
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
        message.success(t('pages.contactsCard.msgSaved'))
      } else {
        await createContact(payload)
        message.success(t('pages.contactsCard.msgCreated'))
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.contactsCard.msgSaveFailed')))
    }
  }

  const onDelete = async (row: Contact) => {
    try {
      await deleteContact(row.id)
      message.success(t('pages.contactsCard.msgDeleted'))
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.contactsCard.msgDeleteFailed')))
    }
  }

  const columns = [
    { title: t('pages.contactsCard.colName'), dataIndex: 'name' },
    { title: t('pages.contactsCard.colTitle'), dataIndex: 'title', render: (v?: string) => v ?? '-' },
    { title: t('pages.contactsCard.colPhone'), dataIndex: 'phone', render: (v?: string) => v ?? '-' },
    { title: t('pages.contactsCard.colEmail'), dataIndex: 'email', render: (v?: string) => v ?? '-' },
    {
      title: t('pages.contactsCard.colRole'),
      dataIndex: 'role',
      render: (role: ContactRole) => (
        <Tag color={ROLE_COLORS[role]}>{labelOf(t, ENUM_KEYS.contactRole, role)}</Tag>
      ),
    },
    {
      title: t('pages.contactsCard.colAction'),
      key: 'actions',
      width: 140,
      render: (_: unknown, row: Contact) => [
        <a key="edit" onClick={() => openEdit(row)}>
          <EditOutlined /> {t('pages.contactsCard.btnEdit')}
        </a>,
        <Popconfirm
          key="delete"
          title={t('pages.contactsCard.confirmDelete', { name: row.name })}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>
            <DeleteOutlined /> {t('pages.contactsCard.btnDelete')}
          </a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <Card
      title={t('pages.contactsCard.title')}
      style={{ marginBottom: 16, borderRadius: 10 }}
      headStyle={{ borderBottom: '1px solid #f0f0f0' }}
      styles={{ body: { padding: 0 } }}
      extra={
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreate}>
          {t('pages.contactsCard.btnAdd')}
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
        locale={{ emptyText: t('pages.contactsCard.empty') }}
      />

      <Modal
        title={editing ? t('pages.contactsCard.modalEdit') : t('pages.contactsCard.modalAdd')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.contactsCard.btnSave')}
        destroyOnClose
        width={560}
      >
        <Form form={form} name="contactCardForm" layout="vertical">
          <Form.Item name="name" label={t('pages.contactsCard.labelName')} rules={[{ required: true, message: t('pages.contactsCard.labelName') }]} style={{ flex: 1 }}>
            <Input />
          </Form.Item>
          <Form.Item name="title" label={t('pages.contactsCard.labelTitle')} style={{ flex: 1 }}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label={t('pages.contactsCard.labelPhone')} style={{ flex: 1 }}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label={t('pages.contactsCard.labelEmail')} style={{ flex: 1 }}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label={t('pages.contactsCard.labelRole')}>
            <Select
              allowClear
              placeholder={t('pages.contactsCard.placeholderRole')}
              options={Object.keys(ENUM_KEYS.contactRole).map((value) => ({
                value,
                label: labelOf(t, ENUM_KEYS.contactRole, value),
              }))}
            />
          </Form.Item>
          <Form.Item name="remark" label={t('pages.contactsCard.labelRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
