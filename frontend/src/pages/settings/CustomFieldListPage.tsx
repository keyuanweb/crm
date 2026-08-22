import { useRef, useState } from 'react'
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
import {
  FIELD_ENTITY_LABELS,
  FIELD_TYPE_LABELS,
  type CustomField,
  type FieldEntityType,
  type FieldType,
} from '../../types/customField'

interface FormValues {
  entityType: FieldEntityType
  name: string
  fieldType: FieldType
  required: boolean
  options?: string
  sortOrder?: number
}

export default function CustomFieldListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CustomField | null>(null)
  const [fieldType, setFieldType] = useState<FieldType>('TEXT')
  const [form] = Form.useForm<FormValues>()

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
    try {
      if (editing) {
        await updateCustomField(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createCustomField(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: CustomField) => {
    try {
      await deleteCustomField(row.id)
      message.success('已删除（历史值已清理）')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<CustomField>[] = [
    {
      title: '适用实体',
      dataIndex: 'entityType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(FIELD_ENTITY_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color="blue">{FIELD_ENTITY_LABELS[row.entityType]}</Tag>,
    },
    { title: '字段名称', dataIndex: 'name' },
    {
      title: '类型',
      dataIndex: 'fieldType',
      search: false,
      render: (_, row) => FIELD_TYPE_LABELS[row.fieldType],
    },
    {
      title: '必填',
      dataIndex: 'required',
      search: false,
      render: (_, row) => (row.required ? <Tag color="red">必填</Tag> : <Tag>可选</Tag>),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '排序', dataIndex: 'sortOrder', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除字段「${row.name}」吗？历史值将被清理。`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<CustomField>
        headerTitle="自定义字段"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchCustomFields(params.entityType, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增字段
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑自定义字段' : '新增自定义字段'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={560}
      >
        <Form form={form} name="customFieldForm" layout="vertical">
          <Form.Item
            name="entityType"
            label="适用实体"
            rules={[{ required: true, message: '请选择实体' }]}
          >
            <Select
              disabled={!!editing}
              options={Object.entries(FIELD_ENTITY_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="name" label="字段名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="fieldType" label="字段类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              disabled={!!editing}
              options={Object.entries(FIELD_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
              onChange={(v) => setFieldType(v as FieldType)}
            />
          </Form.Item>
          {fieldType === 'SELECT' && (
            <Form.Item
              name="options"
              label="选项（逗号分隔）"
              rules={[{ required: true, message: 'SELECT 类型必须提供选项' }]}
            >
              <Input placeholder="如：高,中,低" />
            </Form.Item>
          )}
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <Form.Item name="sortOrder" label="排序" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="required" label="必填" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  )
}
