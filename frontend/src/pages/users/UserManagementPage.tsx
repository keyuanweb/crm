import { useRef, useState } from 'react'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { createUser, fetchUsers, resetPassword, updateUser } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { ROLE_LABELS, type User, type UserRole } from '../../types/user'
import { useAuthStore } from '../../store/authStore'
import dayjs from 'dayjs'

interface CreateValues {
  username: string
  displayName: string
  role: UserRole
  password: string
}

interface EditValues {
  displayName: string
  role: UserRole
}

export default function UserManagementPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [resetOpen, setResetOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [createForm] = Form.useForm<CreateValues>()
  const [editForm] = Form.useForm<EditValues>()
  const [resetForm] = Form.useForm<{ newPassword: string }>()
  const currentUser = useAuthStore((s) => s.user)

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    createForm.resetFields()
    setCreateOpen(true)
  }

  const openEdit = (row: User) => {
    setEditing(row)
    editForm.setFieldsValue({ displayName: row.displayName, role: row.role })
    setEditOpen(true)
  }

  const openReset = (row: User) => {
    setEditing(row)
    resetForm.resetFields()
    setResetOpen(true)
  }

  const onCreate = async () => {
    const values = await createForm.validateFields()
    try {
      await createUser(values)
      message.success('已创建')
      setCreateOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建失败'))
    }
  }

  const onEdit = async () => {
    if (!editing) return
    const values = await editForm.validateFields()
    try {
      await updateUser(editing.id, { ...values, version: editing.version })
      message.success('已保存')
      setEditOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onReset = async () => {
    if (!editing) return
    const values = await resetForm.validateFields()
    try {
      await resetPassword(editing.id, values.newPassword)
      message.success('已重置密码（旧令牌立即失效）')
      setResetOpen(false)
    } catch (err) {
      message.error(extractErrorMessage(err, '重置失败'))
    }
  }

  const onToggle = async (row: User) => {
    if (row.id === currentUser?.id) {
      message.error('不能停用当前登录账号')
      return
    }
    try {
      await updateUser(row.id, { enabled: !row.enabled, version: row.version })
      message.success(row.enabled ? '已停用' : '已启用')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const columns: ProColumns<User>[] = [
    { title: '用户名', dataIndex: 'username' },
    { title: '显示名', dataIndex: 'displayName' },
    {
      title: '角色',
      dataIndex: 'role',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(ROLE_LABELS).map(([value, text]) => [value, { text }]),
      ),
      render: (_, row) => <Tag color={row.role === 'ADMIN' ? 'red' : row.role === 'SALES' ? 'blue' : 'default'}>{ROLE_LABELS[row.role]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      valueType: 'select',
      valueEnum: { true: { text: '启用' }, false: { text: '停用' } },
      render: (_, row) =>
        row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      search: false,
      render: (_, row) => (row.lastLoginAt ? dayjs(row.lastLoginAt).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <a key="reset" style={{ color: '#fa8c16' }} onClick={() => openReset(row)}>
          重置密码
        </a>,
        <Popconfirm
          key="toggle"
          title={`确定${row.enabled ? '停用' : '启用'}账号「${row.username}」吗？`}
          onConfirm={() => onToggle(row)}
        >
          <a style={{ color: '#ff4d4f' }}>{row.enabled ? '停用' : '启用'}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<User>
        headerTitle="用户管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchUsers({
            keyword: params.keyword,
            role: params.role,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增用户
          </Button>,
        ]}
      />

      <Modal
        title="新增用户"
        open={createOpen}
        onOk={() => void onCreate()}
        onCancel={() => setCreateOpen(false)}
        okText="创建"
        destroyOnClose
      >
        <Form form={createForm} name="createUser" layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[a-zA-Z0-9_]{3,50}$/, message: '3~50 位字母/数字/下划线' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true, message: '请输入显示名' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="role"
            label="角色"
            initialValue="SALES"
            rules={[{ required: true, message: '请选择角色' }]}
          >
            <Select
              options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 8, max: 64, message: '8~64 位' },
            ]}
            extra="须同时包含字母与数字"
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? `编辑用户：${editing.username}` : ''}
        open={editOpen}
        onOk={() => void onEdit()}
        onCancel={() => setEditOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={editForm} name="editUser" layout="vertical">
          <Form.Item name="displayName" label="显示名" rules={[{ required: true, message: '请输入显示名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? `重置密码：${editing.username}` : ''}
        open={resetOpen}
        onOk={() => void onReset()}
        onCancel={() => setResetOpen(false)}
        okText="重置"
        destroyOnClose
      >
        <Form form={resetForm} name="resetPasswordForm" layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 64, message: '8~64 位' },
            ]}
            extra="重置后旧令牌立即失效"
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
