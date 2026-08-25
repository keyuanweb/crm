import { useEffect, useRef, useState } from 'react'
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
import type { CustomObject, ObjectRecord } from '../../types/customObject'

/** 自定义对象记录管理页（059，动态表单）。 */
export default function CustomObjectRecordPage() {
  const { id } = useParams<{ id: string }>()
  const objectId = Number(id)
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [object, setObject] = useState<CustomObject | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ObjectRecord | null>(null)
  const [form] = Form.useForm()

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
        message.success('已保存')
      } else {
        await createObjectRecord(objectId, values)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
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
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>编辑</a>,
        <Popconfirm key="del" title="删除该记录？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  const onDelete = async (row: ObjectRecord) => {
    try {
      await deleteObjectRecord(objectId, row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <Link to="/custom-objects" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link">← 返回对象列表</Button>
      </Link>
      <ProTable<ObjectRecord>
        headerTitle={`${object?.name ?? '对象'} · 记录`}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchObjectRecords(objectId, params.keyword, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建记录
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑记录' : '新建记录'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          {object?.fields.map((f) => (
            <Form.Item
              key={f.field}
              name={f.field}
              label={`${f.label}${f.required ? ' *' : ''}`}
              rules={[{ required: !!f.required, message: `请填写${f.label}` }]}
            >
              {renderFieldInput(f)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </>
  )
}

function renderFieldInput(f: { field: string; label: string; type: string; options?: string }) {
  switch (f.type) {
    case 'NUMBER':
      return <InputNumber style={{ width: '100%' }} />
    case 'DATE':
      return <DatePicker style={{ width: '100%' }} />
    case 'SELECT':
      return (
        <Select
          options={(f.options ?? '').split(',').filter(Boolean).map((o) => ({ value: o, label: o }))}
          placeholder={`请选择${f.label}`}
        />
      )
    default:
      return <Input placeholder={`请输入${f.label}`} />
  }
}
