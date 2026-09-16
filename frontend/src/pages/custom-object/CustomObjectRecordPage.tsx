import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createObjectRecord,
  deleteObjectRecord,
  fetchCustomObjects,
  fetchObjectRecords,
  updateObjectRecord,
} from '../../services/customObjectService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { CustomObject, ObjectRecord } from '../../types/customObject'

/** 自定义对象记录管理页（059，动态表单）。 */
export default function CustomObjectRecordPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const objectId = Number(id)
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [object, setObject] = useState<CustomObject | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ObjectRecord | null>(null)
  const [form] = Form.useForm()
  // 记录面三个写码（096 建）。读走 `custom_object_record:read`，是页面取数路径、不做判据。
  const can = usePerms([
    PERMS.customObjectRecordCreate,
    PERMS.customObjectRecordUpdate,
    PERMS.customObjectRecordDelete,
  ])

  // F1(前端审计修复)：改用 useEffect 加载对象定义（原 render 内 fetch 会重复请求/可能死循环）
  useEffect(() => {
    if (!Number.isFinite(objectId)) return
    let alive = true
    void fetchCustomObjects(undefined, 1, 100).then((res) => {
      if (alive) setObject(res.items.find((o) => o.id === objectId) ?? null)
    })
    return () => {
      alive = false
    }
  }, [objectId])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: ObjectRecord) => {
    setEditing(row)
    form.setFieldsValue(row.values ?? {})
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateObjectRecord(objectId, editing.id, values)
        message.success(t('pages.customObject.msgSaved'))
      } else {
        await createObjectRecord(objectId, values)
        message.success(t('pages.customObject.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customObject.msgSaveFailed')))
    }
  }

  const columns: ProColumns<ObjectRecord>[] = [
    { title: 'ID', dataIndex: 'id', search: false },
    ...(object?.fields.map((f) => ({
      title: f.label,
      key: f.field,
      search: false,
      render: (_: unknown, row: ObjectRecord) => row.values?.[f.field] ?? '-',
    })) ?? []),
    {
      title: t('pages.customObject.colCreatedAt'),
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: t('pages.customObject.colAction'),
      valueType: 'option',
      render: (_, row) => [
        can[PERMS.customObjectRecordUpdate] && (
          <a key="edit" onClick={() => openEdit(row)}>
            {t('pages.customObject.edit')}
          </a>
        ),
        can[PERMS.customObjectRecordDelete] && (
          <Popconfirm
            key="del"
            title={t('pages.customObject.confirmDeleteRecord')}
            onConfirm={() => void onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('pages.customObject.delete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  const onDelete = async (row: ObjectRecord) => {
    try {
      await deleteObjectRecord(objectId, row.id)
      message.success(t('pages.customObject.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customObject.msgDeleteFailed')))
    }
  }

  return (
    <>
      <Link to="/custom-objects" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link">{t('pages.customObject.backToList')}</Button>
      </Link>
      <ProTable<ObjectRecord>
        headerTitle={`${object?.name ?? t('pages.customObject.objectLabel')} · ${t('pages.customObject.records')}`}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchObjectRecords(objectId, params.keyword, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() =>
          can[PERMS.customObjectRecordCreate]
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  {t('pages.customObject.btnAddRecord')}
                </Button>,
              ]
            : []
        }
      />
      <Modal
        title={editing ? t('pages.customObject.modalEditRecordTitle') : t('pages.customObject.modalAddRecordTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.customObject.btnSave')}
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          {object?.fields.map((f) => (
            <Form.Item
              key={f.field}
              name={f.field}
              label={`${f.label}${f.required ? ' *' : ''}`}
              rules={[{ required: !!f.required, message: t('common.message.required', { field: f.label }) }]}
            >
              {renderFieldInput(f, t)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </>
  )
}

function renderFieldInput(
  f: { field: string; label: string; type: string; options?: string },
  t: (key: string, params?: Record<string, unknown>) => string,
) {
  switch (f.type) {
    case 'NUMBER':
      return <InputNumber style={{ width: '100%' }} />
    case 'DATE':
      return <DatePicker style={{ width: '100%' }} />
    case 'SELECT':
      return (
        <Select
          options={(f.options ?? '').split(',').filter(Boolean).map((o) => ({ value: o, label: o }))}
          placeholder={t('pages.customObject.selectPlaceholder', { field: f.label })}
        />
      )
    default:
      return <Input placeholder={t('common.message.required', { field: f.label })} />
  }
}
