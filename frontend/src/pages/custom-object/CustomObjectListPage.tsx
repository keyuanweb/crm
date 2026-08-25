import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
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
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CustomObject | null>(null)
  const [form] = Form.useForm<FormValues>()

  const columns: ProColumns<CustomObject>[] = [
    { title: '名称', dataIndex: 'name' },
    { title: '编码', dataIndex: 'code', render: (_, row) => <Tag color="blue">{row.code}</Tag> },
    { title: '字段数', dataIndex: 'fields', search: false, render: (_, row) => row.fields.length },
    {
      title: '状态',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <Link key="records" to={`/custom-objects/${row.id}/records`}>
          记录
        </Link>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <a key="toggle" onClick={() => void onToggle(row)}>
          {row.enabled ? '停用' : '启用'}
        </a>,
        <Popconfirm key="del" title="删除对象？（记录保留）" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
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
        message.success('已保存')
      } else {
        await createCustomObject(values)
        message.success('已创建')
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onToggle = async (row: CustomObject) => {
    try {
      await toggleCustomObject(row.id)
      message.success(row.enabled ? '已停用' : '已启用')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: CustomObject) => {
    try {
      await deleteCustomObject(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <ProTable<CustomObject>
        headerTitle="自定义对象"
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
                fields: [{ field: 'name', label: '名称', type: 'TEXT', required: true }],
              })
              setModalOpen(true)
            }}
          >
            新建对象
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑对象' : '新建对象'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="对象名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={100} placeholder="如：项目" />
          </Form.Item>
          <Form.Item name="code" label="对象编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]*$/, message: '大写字母开头，含数字/下划线' }]}>
            <Input maxLength={50} placeholder="如：PROJECT" disabled={!!editing} />
          </Form.Item>
          <Form.List name="fields" rules={[{ validator: async (_, v) => { if (!v || v.length === 0) throw new Error('至少一个字段') } }]}>
            {(fieldList, { add, remove }) => (
              <>
                {fieldList.map((field) => (
                  <div key={field.key} style={{ display: 'flex', gap: 8, alignItems: 'baseline', marginBottom: 8 }}>
                    <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: '字段名' }]} style={{ flex: 1 }}>
                      <Input placeholder="字段名（英文）" />
                    </Form.Item>
                    <Form.Item name={[field.name, 'label']} rules={[{ required: true, message: '标签' }]} style={{ flex: 1 }}>
                      <Input placeholder="显示标签" />
                    </Form.Item>
                    <Form.Item name={[field.name, 'type']} rules={[{ required: true }]} style={{ width: 120 }}>
                      <Select
                        options={[
                          { value: 'TEXT', label: '文本' },
                          { value: 'NUMBER', label: '数字' },
                          { value: 'DATE', label: '日期' },
                          { value: 'SELECT', label: '下拉' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'required']} valuePropName="checked">
                      <Switch checkedChildren="必填" unCheckedChildren="可选" />
                    </Form.Item>
                    <MinusCircle onClick={() => remove(field.name)} />
                  </div>
                ))}
                <Button type="dashed" onClick={() => add({ field: '', label: '', type: 'TEXT', required: false })} icon={<PlusOutlined />} block>
                  添加字段
                </Button>
              </>
            )}
          </Form.List>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function MinusCircle({ onClick }: { onClick: () => void }) {
  return (
    <Button danger type="text" size="small" onClick={onClick}>
      移除
    </Button>
  )
}
