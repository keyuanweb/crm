import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons'
import {
  createCustomObject,
  deleteCustomObject,
  fetchCustomObjects,
  toggleCustomObject,
  updateCustomObject,
} from '../../services/customObjectService'
import { extractErrorMessage } from '../../services/apiClient'
import type { CustomObject, ObjectFieldDef } from '../../types/customObject'

interface FormValues {
  name: string
  code: string
  enabled: boolean
  fields: ObjectFieldDef[]
}

/** 自定义对象定义页（059，仅 ADMIN）。 */
export default function CustomObjectListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CustomObject | null>(null)
  const [form] = Form.useForm<FormValues>()

  const columns: ProColumns<CustomObject>[] = [
    { title: t('pages.customObject.colName'), dataIndex: 'name' },
    { title: t('pages.customObject.colCode'), dataIndex: 'code', render: (_, row) => <Tag color="blue">{row.code}</Tag> },
    { title: t('pages.customObject.colFieldCount'), dataIndex: 'fields', search: false, render: (_, row) => row.fields.length },
    {
      title: t('pages.customObject.colStatus'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.customObject.enabled')}</Tag> : <Tag>{t('pages.customObject.disabled')}</Tag>),
    },
    {
      title: t('pages.customObject.colAction'),
      valueType: 'option',
      render: (_, row) => [
        <Link key="records" to={`/custom-objects/${row.id}/records`}>
          {t('pages.customObject.records')}
        </Link>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.customObject.edit')}
        </a>,
        <a key="toggle" onClick={() => void onToggle(row)}>
          {row.enabled ? t('pages.customObject.disable') : t('pages.customObject.enable')}
        </a>,
        <Popconfirm key="del" title={t('pages.customObject.confirmDelete')} onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>{t('pages.customObject.delete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  const openEdit = (row: CustomObject) => {
    setEditing(row)
    form.setFieldsValue({
      name: row.name,
      code: row.code,
      enabled: row.enabled,
      fields: row.fields,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateCustomObject(editing.id, { ...values, version: editing.version })
        message.success(t('pages.customObject.msgSaved'))
      } else {
        await createCustomObject(values)
        message.success(t('pages.customObject.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customObject.msgSaveFailed')))
    }
  }

  const onToggle = async (row: CustomObject) => {
    try {
      await toggleCustomObject(row.id)
      message.success(row.enabled ? t('pages.customObject.msgDisabled') : t('pages.customObject.msgEnabled'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customObject.msgOperationFailed')))
    }
  }

  const onDelete = async (row: CustomObject) => {
    try {
      await deleteCustomObject(row.id)
      message.success(t('pages.customObject.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customObject.msgDeleteFailed')))
    }
  }

  return (
    <>
      <ProTable<CustomObject>
        headerTitle={t('pages.customObject.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchCustomObjects(params.keyword, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              form.resetFields()
              form.setFieldsValue({
                enabled: true,
                fields: [{ field: 'name', label: t('pages.customObject.fieldName'), type: 'TEXT', required: true }],
              })
              setModalOpen(true)
            }}
          >
            {t('pages.customObject.btnAdd')}
          </Button>,
        ]}
      />
      <Modal
        title={editing ? t('pages.customObject.modalEditTitle') : t('pages.customObject.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.customObject.btnSave')}
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('pages.customObject.formNameLabel')} rules={[{ required: true, message: t('pages.customObject.formNameRequired') }]}>
            <Input maxLength={100} placeholder={t('pages.customObject.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="code" label={t('pages.customObject.formCodeLabel')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]*$/, message: t('pages.customObject.formCodeRequired') }]}>
            <Input maxLength={50} placeholder={t('pages.customObject.formCodePlaceholder')} disabled={!!editing} />
          </Form.Item>
          <Form.List name="fields" rules={[{ validator: async (_, v) => { if (!v || v.length === 0) throw new Error(t('pages.customObject.formFieldsRequired')) } }]}>
            {(fieldList, { add, remove }) => (
              <>
                {fieldList.map((field) => (
                  <div key={field.key} style={{ display: 'flex', gap: 8, alignItems: 'baseline', marginBottom: 8 }}>
                    <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: t('pages.customObject.fieldNameRequired') }]} style={{ flex: 1 }}>
                      <Input placeholder={t('pages.customObject.fieldNamePlaceholder')} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'label']} rules={[{ required: true, message: t('pages.customObject.fieldLabelRequired') }]} style={{ flex: 1 }}>
                      <Input placeholder={t('pages.customObject.fieldLabelPlaceholder')} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'type']} rules={[{ required: true }]} style={{ width: 120 }}>
                      <Select
                        options={[
                          { value: 'TEXT', label: t('pages.customObject.fieldTypeText') },
                          { value: 'NUMBER', label: t('pages.customObject.fieldTypeNumber') },
                          { value: 'DATE', label: t('pages.customObject.fieldTypeDate') },
                          { value: 'SELECT', label: t('pages.customObject.fieldTypeSelect') },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'required']} valuePropName="checked">
                      <Switch checkedChildren={t('pages.customObject.required')} unCheckedChildren={t('pages.customObject.optional')} />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(field.name)} style={{ fontSize: 16, color: '#ff4d4f', cursor: 'pointer' }} />
                  </div>
                ))}
                <Button type="dashed" onClick={() => add({ field: '', label: '', type: 'TEXT', required: false })} icon={<PlusOutlined />} block>
                  {t('pages.customObject.btnAddField')}
                </Button>
              </>
            )}
          </Form.List>
          <Form.Item name="enabled" label={t('pages.customObject.formEnabledLabel')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
