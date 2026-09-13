import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Table,
  Tag,
  Upload,
} from 'antd'
import { DeleteOutlined, DownloadOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import {
  createContact,
  deleteContact,
  downloadContactTemplate,
  fetchContacts,
  importContacts,
  updateContact,
  type ContactPayload,
} from '../../services/contactService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { ROLE_COLORS, type Contact, type ContactRole } from '../../types/contact'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import type { ImportResult } from '../../types/importResult'

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
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Contact | null>(null)
  const [customerOptions, setCustomerOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importing, setImporting] = useState(false)
  // 删除联系人走 DELETE /contacts/{id}，ContactController 上标的是 contact:delete。
  const can = usePerms([PERMS.contactDelete])

  const reload = () => actionRef.current?.reload()

  const onImport = async (file: File) => {
    setImporting(true)
    try {
      const res = await importContacts(file)
      setImportResult(res)
      setImportOpen(true)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setImporting(false)
    }
    return false
  }

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
    setSaving(true)
    try {
      if (editing) {
        await updateContact(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.contact.list.msgSaved'))
      } else {
        await createContact(payload)
        message.success(t('pages.contact.list.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Contact) => {
    try {
      await deleteContact(row.id)
      message.success(t('pages.contact.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const columns: ProColumns<Contact>[] = [
    {
      title: t('pages.contact.list.colName'),
      dataIndex: 'name',
      render: (_, row) => <Link to={`/customers/${row.customerId}`}>{row.name}</Link>,
    },
    {
      title: t('pages.contact.list.colCustomer'),
      dataIndex: 'customerName',
      render: (_, row) =>
        row.customerId ? <Link to={`/customers/${row.customerId}`}>{row.customerName ?? '-'}</Link> : '-',
    },
    { title: t('pages.contact.list.colTitle'), dataIndex: 'title', search: false },
    { title: t('pages.contact.list.colPhone'), dataIndex: 'phone', search: false },
    { title: t('pages.contact.list.colEmail'), dataIndex: 'email', search: false },
    {
      title: t('pages.contact.list.colRole'),
      dataIndex: 'role',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.contactRole).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.contactRole, code) },
        ]),
      ),
      render: (_, row) => (
        <Tag color={ROLE_COLORS[row.role]}>{labelOf(t, ENUM_KEYS.contactRole, row.role)}</Tag>
      ),
    },
    {
      title: t('pages.contact.list.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          <EditOutlined /> {t('pages.contact.list.edit')}
        </a>,
        can[PERMS.contactDelete] ? (
          <Popconfirm key="delete" title={t('pages.contact.list.deleteConfirm', { name: row.name })} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> {t('pages.contact.list.delete')}
            </a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<Contact>
        size="small"
        headerTitle={t('pages.contact.list.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
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
          <Upload key="import" showUploadList={false} beforeUpload={(f) => onImport(f as unknown as File)} accept=".xlsx">
            <Button icon={<UploadOutlined />} loading={importing}>{t('pages.contact.list.import')}</Button>
          </Upload>,
          <Button key="template" icon={<DownloadOutlined />} onClick={() => void downloadContactTemplate()}>
            {t('pages.contact.list.downloadTemplate')}
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.contact.list.create')}
          </Button>,
        ]}
      />

      {/* 导入结果反馈 */}
      <Modal
        title={t('pages.contact.list.importResult')}
        open={importOpen}
        footer={null}
        onCancel={() => setImportOpen(false)}
      >
        <div style={{ marginBottom: 12 }}>
          <Tag color="green">{t('pages.contact.list.importSuccess', { count: importResult?.successCount ?? 0 })}</Tag>
          <Tag color="red">{t('pages.contact.list.importFail', { count: importResult?.failureCount ?? 0 })}</Tag>
        </div>
        {(importResult?.failures ?? []).length > 0 && (
          <Table
            rowKey={(r, i) => `${(r as { row: number }).row}-${i}`}
            size="small"
            dataSource={importResult?.failures ?? []}
            pagination={false}
            columns={[
              { title: t('pages.contact.list.colRow'), dataIndex: 'row', width: 80 },
              { title: t('pages.contact.list.colFail'), dataIndex: 'message' },
            ]}
          />
        )}
      </Modal>

      <Modal
        title={editing ? t('pages.contact.list.editModal') : t('pages.contact.list.createModal')}
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
          name="contactForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Form.Item
            name="customerId"
            label={t('pages.contact.list.colCustomer')}
            rules={[{ required: true, message: t('pages.contract.list.msgCustomerRequired') }]}
          >
            <Select
              showSearch
              placeholder={t('pages.contract.list.phCustomer')}
              options={customerOptions}
              filterOption={false}
              onSearch={(kw) => void loadCustomers(kw)}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label={t('pages.contact.list.colName')} rules={[{ required: true, message: t('pages.lead.list.msgNameRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label={t('pages.contact.list.colTitle')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label={t('pages.contact.list.colPhone')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="email" label={t('pages.contact.list.colEmail')}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="role" label={t('pages.contact.list.colRole')}>
                <Select
                  allowClear
                  placeholder="Default: Other"
                  options={Object.keys(ENUM_KEYS.contactRole).map((value) => ({
                    value,
                    label: labelOf(t, ENUM_KEYS.contactRole, value),
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label={t('pages.contact.list.formRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
