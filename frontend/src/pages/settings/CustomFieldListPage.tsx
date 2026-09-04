import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createCustomField,
  deleteCustomField,
  fetchCustomFields,
  updateCustomField,
  type CustomFieldPayload,
} from '../../services/customFieldService'
import { extractErrorMessage } from '../../services/apiClient'
import type { CustomField, FieldEntityType, FieldType } from '../../types/customField'

interface FormValues {
  entityType: FieldEntityType
  name: string
  fieldType: FieldType
  required: boolean
  options?: string
  sortOrder?: number
}

export default function CustomFieldListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<CustomField | null>(null)
  const [fieldType, setFieldType] = useState<FieldType>('TEXT')
  const [form] = Form.useForm<FormValues>()

  const FIELD_ENTITY_LABELS: Record<FieldEntityType, string> = {
    LEAD: t('pages.customField.fieldEntityLead'),
    CUSTOMER: t('pages.customField.fieldEntityCustomer'),
    OPPORTUNITY: t('pages.customField.fieldEntityOpportunity'),
    TICKET: t('pages.customField.fieldEntityTicket'),
  }

  const FIELD_TYPE_LABELS: Record<FieldType, string> = {
    TEXT: t('pages.customField.fieldTypeText'),
    TEXTAREA: t('pages.customField.fieldTypeTextarea'),
    NUMBER: t('pages.customField.fieldTypeNumber'),
    DATE: t('pages.customField.fieldTypeDate'),
    SELECT: t('pages.customField.fieldTypeSelect'),
  }

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setFieldType('TEXT')
    form.setFieldsValue({ entityType: 'LEAD', fieldType: 'TEXT', required: false })
    setModalOpen(true)
  }

  const openEdit = (row: CustomField) => {
    setEditing(row)
    setFieldType(row.fieldType)
    form.setFieldsValue({
      entityType: row.entityType,
      name: row.name,
      fieldType: row.fieldType,
      required: row.required,
      options: row.options,
      sortOrder: row.sortOrder,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: CustomFieldPayload = {
      entityType: values.entityType,
      name: values.name.trim(),
      fieldType: values.fieldType,
      required: values.required,
      options: values.options,
      sortOrder: values.sortOrder,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateCustomField(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.customField.msgSaved'))
      } else {
        await createCustomField(payload)
        message.success(t('pages.customField.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customField.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: CustomField) => {
    try {
      await deleteCustomField(row.id)
      message.success(t('pages.customField.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customField.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<CustomField>[] = [
    {
      title: t('pages.customField.colEntityType'),
      dataIndex: 'entityType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(FIELD_ENTITY_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color="blue">{FIELD_ENTITY_LABELS[row.entityType]}</Tag>,
    },
    { title: t('pages.customField.colName'), dataIndex: 'name' },
    {
      title: t('pages.customField.colFieldType'),
      dataIndex: 'fieldType',
      search: false,
      render: (_, row) => FIELD_TYPE_LABELS[row.fieldType],
    },
    {
      title: t('pages.customField.colRequired'),
      dataIndex: 'required',
      search: false,
      render: (_, row) => (row.required ? <Tag color="red">{t('pages.customField.required')}</Tag> : <Tag>{t('pages.customField.optional')}</Tag>),
    },
    {
      title: t('pages.customField.colEnabled'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.customField.enabled')}</Tag> : <Tag>{t('pages.customField.disabled')}</Tag>),
    },
    { title: t('pages.customField.colSortOrder'), dataIndex: 'sortOrder', search: false },
    {
      title: t('pages.customField.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.customField.edit')}
        </a>,
        <Popconfirm
          key="delete"
          title={t('pages.customField.confirmDelete', { name: row.name })}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>{t('pages.customField.delete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<CustomField>
        size="small"
        headerTitle={t('pages.customField.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchCustomFields(params.entityType, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.customField.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.customField.modalEditTitle') : t('pages.customField.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.customField.btnSave')}
        destroyOnClose
        width={560}
      >
        <Form form={form} name="customFieldForm" layout="vertical">
          <Form.Item
            name="entityType"
            label={t('pages.customField.formEntityTypeLabel')}
            rules={[{ required: true, message: t('pages.customField.formEntityTypeRequired') }]}
          >
            <Select
              disabled={!!editing}
              options={Object.entries(FIELD_ENTITY_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="name" label={t('pages.customField.formNameLabel')} rules={[{ required: true, message: t('pages.customField.formNameRequired') }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="fieldType" label={t('pages.customField.formFieldTypeLabel')} rules={[{ required: true, message: t('pages.customField.formFieldTypeRequired') }]}>
            <Select
              disabled={!!editing}
              options={Object.entries(FIELD_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
              onChange={(v) => setFieldType(v as FieldType)}
            />
          </Form.Item>
          {fieldType === 'SELECT' && (
            <Form.Item
              name="options"
              label={t('pages.customField.formOptionsLabel')}
              rules={[{ required: true, message: t('pages.customField.formOptionsRequired') }]}
            >
              <Input placeholder={t('pages.customField.formOptionsPlaceholder')} />
            </Form.Item>
          )}
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <Form.Item name="sortOrder" label={t('pages.customField.formSortOrderLabel')} style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="required" label={t('pages.customField.formRequiredLabel')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  )
}
