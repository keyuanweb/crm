import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd'
import { CopyOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  createForm,
  deleteForm,
  fetchForms,
  fetchSubmissions,
  toggleForm,
  updateForm,
} from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { FormField, OnlineForm, Submission } from '../../types/form'

interface FieldRow extends FormField {
  key: number
}

export default function OnlineFormPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<OnlineForm | null>(null)
  const [fields, setFields] = useState<FieldRow[]>([])
  const [form] = Form.useForm<{ name: string; successMessage?: string; source?: string }>()
  const [subDrawer, setSubDrawer] = useState<OnlineForm | null>(null)
  const [submissions, setSubmissions] = useState<Submission[]>([])
  const nextKey = useRef(1)
  // 086：启停与删除按权限码收口（toggle / delete 两个端点挂的都是 form:manage）。
  const can = usePerms([PERMS.formManage])

  const FIELD_TYPES = [
    { value: 'TEXT', label: t('pages.marketing.onlineForm.fieldType.TEXT') },
    { value: 'TEL', label: t('pages.marketing.onlineForm.fieldType.TEL') },
    { value: 'EMAIL', label: t('pages.marketing.onlineForm.fieldType.EMAIL') },
    { value: 'TEXTAREA', label: t('pages.marketing.onlineForm.fieldType.TEXTAREA') },
  ]

  const reload = () => actionRef.current?.reload()

  const addField = () => {
    setFields([...fields, { key: nextKey.current++, field: '', label: '', type: 'TEXT', required: false }])
  }

  const updateField = (key: number, patch: Partial<FieldRow>) => {
    setFields(fields.map((f) => (f.key === key ? { ...f, ...patch } : f)))
  }

  const removeField = (key: number) => {
    setFields(fields.filter((f) => f.key !== key))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setFields([
      {
        key: nextKey.current++,
        field: 'name',
        label: t('pages.marketing.onlineForm.defaultFieldName'),
        type: 'TEXT',
        required: true,
      },
    ])
    setModalOpen(true)
  }

  const openEdit = (row: OnlineForm) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, successMessage: row.successMessage, source: row.source })
    try {
      const parsed = JSON.parse(row.fields) as FormField[]
      setFields(parsed.map((f) => ({ ...f, key: nextKey.current++ })))
    } catch {
      setFields([])
    }
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const validFields = fields.filter((f) => f.field.trim() && f.label.trim())
    if (validFields.length === 0) {
      message.warning(t('pages.marketing.onlineForm.msgAtLeastOneField'))
      return
    }
    setSaving(true)
    try {
      const payload = {
        name: values.name.trim(),
        fields: validFields.map((f) => ({ field: f.field, label: f.label, type: f.type, required: f.required })),
        successMessage: values.successMessage,
        source: values.source,
      }
      if (editing) {
        await updateForm(editing.id, payload)
        message.success(t('pages.marketing.onlineForm.msgSaved'))
      } else {
        await createForm(payload)
        message.success(t('pages.marketing.onlineForm.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: OnlineForm) => {
    try {
      await deleteForm(row.id)
      message.success(t('pages.marketing.onlineForm.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.onlineForm.msgDeleteFailed')))
    }
  }

  const onToggle = async (row: OnlineForm, checked: boolean) => {
    try {
      await toggleForm(row.id)
      message.success(
        checked ? t('pages.marketing.onlineForm.msgEnabled') : t('pages.marketing.onlineForm.msgDisabled'),
      )
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.onlineForm.msgOperationFailed')))
    }
  }

  const copyLink = (row: OnlineForm) => {
    const url = `${window.location.origin}/f/${row.id}`
    void navigator.clipboard.writeText(url)
    message.success(t('pages.marketing.onlineForm.msgLinkCopied', { url }))
  }

  const openSubmissions = async (row: OnlineForm) => {
    setSubDrawer(row)
    const res = await fetchSubmissions(row.id, { page: 1, pageSize: 50 })
    setSubmissions(res.items)
  }

  const columns: ProColumns<OnlineForm>[] = [
    { title: t('pages.marketing.onlineForm.colName'), dataIndex: 'name' },
    {
      title: t('pages.marketing.onlineForm.colSource'),
      dataIndex: 'source',
      width: 90,
      render: (_, row) => <Tag color="geekblue">{row.source}</Tag>,
    },
    {
      title: t('pages.marketing.onlineForm.colFieldCount'),
      search: false,
      width: 80,
      render: (_, row) => (JSON.parse(row.fields) as FormField[]).length,
    },
    {
      title: t('pages.marketing.onlineForm.colSubmissionCount'),
      dataIndex: 'submissionCount',
      width: 80,
      search: false,
    },
    {
      title: t('pages.marketing.onlineForm.colStatus'),
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) =>
        can[PERMS.formManage] ? (
          <Switch checked={row.status === 'ENABLED'} size="small" onChange={(c) => void onToggle(row, c)} />
        ) : null,
    },
    {
      title: t('pages.marketing.onlineForm.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="link" onClick={() => copyLink(row)}>
          <CopyOutlined /> {t('pages.marketing.onlineForm.btnExternalLink')}
        </a>,
        <a key="subs" onClick={() => void openSubmissions(row)}>
          {t('pages.marketing.onlineForm.btnRecords')}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.marketing.onlineForm.btnEdit')}
        </a>,
        can[PERMS.formManage] ? (
          <Popconfirm
            key="delete"
            title={t('pages.marketing.onlineForm.confirmDelete')}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('pages.marketing.onlineForm.btnDelete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<OnlineForm>
        size="small"
        headerTitle={t('pages.marketing.onlineForm.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchForms()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.marketing.onlineForm.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={
          editing
            ? t('pages.marketing.onlineForm.modalEditTitle')
            : t('pages.marketing.onlineForm.modalCreateTitle')
        }
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.marketing.onlineForm.btnSave')}
        destroyOnClose
        width={720}
      >
        <Form form={form} name="formDef" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label={t('pages.marketing.onlineForm.formName')}
                rules={[{ required: true, message: t('pages.marketing.onlineForm.msgNameRequired') }]}
              >
                <Input placeholder={t('pages.marketing.onlineForm.formNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="source" label={t('pages.marketing.onlineForm.formSource')}>
                <Select
                  options={[
                    { value: 'WEBSITE', label: t('pages.marketing.onlineForm.sourceWebsite') },
                    { value: 'ADVERTISEMENT', label: t('pages.marketing.onlineForm.sourceAdvertisement') },
                    { value: 'EXHIBITION', label: t('pages.marketing.onlineForm.sourceExhibition') },
                    { value: 'OTHER', label: t('pages.marketing.onlineForm.sourceOther') },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="successMessage" label={t('pages.marketing.onlineForm.formSuccessMessage')}>
            <Input placeholder={t('pages.marketing.onlineForm.formSuccessMessagePlaceholder')} />
          </Form.Item>
        </Form>

        <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 6 }}>
          {t('pages.marketing.onlineForm.formFieldsLabel')}
        </div>
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 10, background: '#fafafa' }}>
          {fields.map((f) => (
            <Row key={f.key} gutter={8} style={{ marginBottom: 8 }} align="middle">
              <Col span={5}>
                <Input value={f.field} onChange={(e) => updateField(f.key, { field: e.target.value })} placeholder="field" />
              </Col>
              <Col span={7}>
                <Input
                  value={f.label}
                  onChange={(e) => updateField(f.key, { label: e.target.value })}
                  placeholder={t('pages.marketing.onlineForm.phDisplayLabel')}
                />
              </Col>
              <Col span={6}>
                <Select value={f.type} onChange={(v) => updateField(f.key, { type: v })} style={{ width: '100%' }} options={FIELD_TYPES} />
              </Col>
              <Col span={3}>
                <Space size={4}>
                  <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                    {t('pages.marketing.onlineForm.labelRequired')}
                  </span>
                  <Switch size="small" checked={f.required} onChange={(c) => updateField(f.key, { required: c })} />
                </Space>
              </Col>
              <Col span={3}>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeField(f.key)} />
              </Col>
            </Row>
          ))}
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addField} block>
            {t('pages.marketing.onlineForm.btnAddField')}
          </Button>
        </div>
      </Modal>

      <Drawer
        title={t('pages.marketing.onlineForm.drawerTitle', { name: subDrawer?.name ?? '' })}
        open={!!subDrawer}
        onClose={() => setSubDrawer(null)}
        width={560}
      >
        {submissions.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#8c8c8c', padding: 40 }}>
            {t('pages.marketing.onlineForm.emptySubmissions')}
          </div>
        ) : (
          <Table<Submission>
            size="small"
            rowKey="id"
            dataSource={submissions}
            pagination={false}
            columns={[
              {
                title: t('pages.marketing.onlineForm.colSubmissionContent'),
                dataIndex: 'payload',
                render: (v: string) => {
                  try {
                    const obj = JSON.parse(v) as Record<string, string>
                    return Object.entries(obj)
                      .map(([k, val]) => `${k}: ${val}`)
                      .join('；')
                  } catch {
                    return v
                  }
                },
              },
              {
                title: t('pages.marketing.onlineForm.colIp'),
                dataIndex: 'clientIp',
                width: 120,
                render: (v?: string) => v || '-',
              },
              {
                title: t('pages.marketing.onlineForm.colLead'),
                dataIndex: 'leadId',
                width: 70,
                render: (v?: number) => (v ? `#${v}` : '-'),
              },
              {
                title: t('pages.marketing.onlineForm.colTime'),
                dataIndex: 'createdAt',
                width: 140,
                render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
              },
            ]}
          />
        )}
      </Drawer>
    </>
  )
}
