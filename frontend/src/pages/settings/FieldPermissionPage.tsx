import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  deleteFieldPermission,
  fetchFieldPermissions,
  upsertFieldPermission,
} from '../../services/fieldPermissionService'
import { fetchCustomFields } from '../../services/customFieldService'
import { extractErrorMessage } from '../../services/apiClient'
import { FIELD_ENTITY_LABELS, FIELD_PERMISSION_LABELS, type FieldPermission } from '../../types/fieldPermission'

interface FormValues {
  roleCode: string
  entityType: string
  fieldId: number
  permission: string
}

const ROLE_OPTIONS = ['ADMIN', 'SALES', 'SUPPORT', 'SERVICE'].map((r) => ({ value: r, label: r }))

/** 字段权限配置页（056，仅 ADMIN）。 */
export default function FieldPermissionPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [fieldOptions, setFieldOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

  const loadFields = async (entityType: string) => {
    try {
      const res = await fetchCustomFields(entityType, 1, 100)
      setFieldOptions(res.items.map((f) => ({ value: f.id, label: f.name })))
    } catch {
      setFieldOptions([])
    }
  }

  const columns: ProColumns<FieldPermission>[] = [
    { title: '角色', dataIndex: 'roleCode', valueType: 'select', valueEnum: Object.fromEntries(ROLE_OPTIONS.map((r) => [r, { text: r }])) },
    { title: '实体', dataIndex: 'entityType', render: (_, row) => FIELD_ENTITY_LABELS[row.entityType] ?? row.entityType },
    { title: '字段', dataIndex: 'fieldId', search: false },
    {
      title: '权限',
      dataIndex: 'permission',
      search: false,
      render: (_, row) => {
        const color = row.permission === 'HIDDEN' ? 'red' : row.permission === 'READ_ONLY' ? 'orange' : 'green'
        return <Tag color={color}>{FIELD_PERMISSION_LABELS[row.permission] ?? row.permission}</Tag>
      },
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <Popconfirm key="del" title="删除该权限配置？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  const onDelete = async (row: FieldPermission) => {
    try {
      await deleteFieldPermission(row.id)
      message.success('已删除')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      await upsertFieldPermission(values)
      message.success('已保存')
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  return (
    <>
      <ProTable<FieldPermission>
        headerTitle="字段级读写权限"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchFieldPermissions(params.roleCode, params.entityType, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields()
              setModalOpen(true)
            }}
          >
            新建权限
          </Button>,
        ]}
      />
      <Modal title="新建字段权限" open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText="保存" destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="roleCode" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select options={ROLE_OPTIONS} placeholder="选择角色" />
          </Form.Item>
          <Form.Item name="entityType" label="实体" rules={[{ required: true, message: '请选择实体' }]}>
            <Select
              options={Object.entries(FIELD_ENTITY_LABELS).map(([value, label]) => ({ value, label }))}
              onChange={(v: string) => void loadFields(v)}
              placeholder="选择实体类型"
            />
          </Form.Item>
          <Form.Item name="fieldId" label="字段" rules={[{ required: true, message: '请选择字段' }]}>
            <Select options={fieldOptions} placeholder="选择自定义字段" />
          </Form.Item>
          <Form.Item name="permission" label="权限" rules={[{ required: true, message: '请选择权限' }]} initialValue="READ_ONLY">
            <Select
              options={Object.entries(FIELD_PERMISSION_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
