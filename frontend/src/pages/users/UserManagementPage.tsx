import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { createUser, fetchUsers, resetPassword, updateUser } from '../../services/userService'
import { fetchDepartmentTree, setUserDataPermission } from '../../services/departmentService'
import { extractErrorMessage } from '../../services/apiClient'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { type User, type UserRole } from '../../types/user'
import { type DataScope } from '../../types/department'
import { useAuthStore } from '../../store/authStore'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
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

/**
 * 角色标签配色。
 *
 * <p>改造前是 `role === 'ADMIN' ? 'red' : role === 'SALES' ? 'blue' : 'default'`：只区分 3 个内建角色，
 * 081 的 10 个角色（以及管理员自建的角色）一律落到 `default`，于是「销售总监」「销售代表」「客服主管」
 * 「客服专员」四种截然不同的职责在列表里长得一模一样。按**前缀**分组而不是逐个枚举，是为了让
 * 管理员自建的 `SALES_INTERN` 这类角色也能归到销售色系，而不是掉进 default。
 */
function roleTagColor(role: string): string {
  if (role === 'ADMIN') return 'red'
  if (role.startsWith('SALES')) return 'blue'
  if (role.startsWith('SUPPORT')) return 'green'
  if (role.startsWith('MARKETING')) return 'purple'
  if (role.startsWith('FINANCE')) return 'gold'
  if (role === 'ANALYST') return 'cyan'
  return 'default'
}

export default function UserManagementPage() {
  const actionRef = useRef<ActionType>()
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [resetOpen, setResetOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [createForm] = Form.useForm<CreateValues>()
  const [editForm] = Form.useForm<EditValues>()
  const [resetForm] = Form.useForm<{ newPassword: string }>()
  const [permOpen, setPermOpen] = useState(false)
  const [permForm] = Form.useForm<{ departmentId?: number; dataScope?: DataScope }>()
  const [deptOptions, setDeptOptions] = useState<{ value: number; label: string }[]>([])
  // 028：角色下拉来自角色列表
  const [roleOptions, setRoleOptions] = useState<{ value: string; label: string }[]>([])
  // roleOptions 的加载在 useEffect 里，而 effect 在首帧之后才跑——首帧 roleOptions 是空数组，
  // 若把创建表单的 Select 直接绑上去，用户看到的会是一个空下拉。用 ENUM_KEYS.userRole 兜底首帧
  // （它覆盖 13 个内建角色），加载完成后自动切换成角色表（含管理员自建的角色）。
  const roleSelectOptions = roleOptions.length
    ? roleOptions
    : Object.keys(ENUM_KEYS.userRole).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.userRole, code) }))
  const currentUser = useAuthStore((s) => s.user)
  // 数据权限 / 重置密码 / 启停三个动作打的是同一个码：PUT /users/{id}、/users/{id}/password、
  // /users/{id}/data-permission 挂的都是 user:manage（UserController.java:76-94），故共用判据。
  // 「编辑」不在收口范围内，保持无条件渲染。
  const can = usePerms([PERMS.userManage])

  const reload = () => actionRef.current?.reload()

  // 028：加载角色下拉（启用角色）
  const loadRoles = async () => {
    try {
      const { fetchRoleOptions } = await import('../../services/roleService')
      const roles = await fetchRoleOptions()
      setRoleOptions(roles.map((r) => ({ value: r.code, label: r.name })))
    } catch {
      // 角色加载失败不阻塞
    }
  }

  const openCreate = () => {
    createForm.resetFields()
    void loadRoles()
    setCreateOpen(true)
  }

  const openEdit = (row: User) => {
    setEditing(row)
    editForm.setFieldsValue({ displayName: row.displayName, role: row.role })
    void loadRoles()
    setEditOpen(true)
  }

  const openReset = (row: User) => {
    setEditing(row)
    resetForm.resetFields()
    setResetOpen(true)
  }

  const openPermission = async (row: User) => {
    setEditing(row)
    const depts = await fetchDepartmentTree()
    const options: { value: number; label: string }[] = []
    const walk = (nodes: typeof depts, prefix: string) => {
      for (const n of nodes) {
        options.push({ value: n.id, label: prefix + n.name })
        walk(n.children, prefix + '　')
      }
    }
    walk(depts, '')
    setDeptOptions(options)
    permForm.setFieldsValue({
      departmentId: row.departmentId,
      dataScope: (row.dataScope as DataScope) ?? 'SELF',
    })
    setPermOpen(true)
  }

  const onPermission = async () => {
    const values = await permForm.validateFields()
    try {
      await setUserDataPermission(editing!.id, values)
      message.success(t('pages.userManagement.messages.saved'))
      setPermOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.userManagement.messages.saveFailed')))
    }
  }

  const onCreate = async () => {
    const values = await createForm.validateFields()
    try {
      await createUser(values)
      message.success(t('pages.userManagement.messages.created'))
      setCreateOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.userManagement.messages.createFailed')))
    }
  }

  const onEdit = async () => {
    if (!editing) return
    const values = await editForm.validateFields()
    try {
      await updateUser(editing.id, { ...values, version: editing.version })
      message.success(t('pages.userManagement.messages.saved'))
      setEditOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.userManagement.messages.saveFailed')))
    }
  }

  const onReset = async () => {
    if (!editing) return
    const values = await resetForm.validateFields()
    try {
      await resetPassword(editing.id, values.newPassword)
      message.success(t('pages.userManagement.messages.passwordReset'))
      setResetOpen(false)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.userManagement.messages.resetFailed')))
    }
  }

  const onToggle = async (row: User) => {
    if (row.id === currentUser?.id) {
      message.error(t('pages.userManagement.messages.cannotDisableSelf'))
      return
    }
    try {
      await updateUser(row.id, { enabled: !row.enabled, version: row.version })
      message.success(row.enabled ? t('pages.userManagement.messages.disabled') : t('pages.userManagement.messages.enabled'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.userManagement.messages.operationFailed')))
    }
  }

  const columns: ProColumns<User>[] = [
    { title: t('pages.userManagement.colUsername'), dataIndex: 'username' },
    { title: t('pages.userManagement.colDisplayName'), dataIndex: 'displayName' },
    {
      title: t('pages.userManagement.colRole'),
      dataIndex: 'role',
      valueType: 'select',
      // 筛选下拉用**角色表**而不是枚举表：枚举表只有内建的 13 个角色，管理员自建的角色
      // （角色页可以新建）会永远筛不出来。roleOptions 来自 /roles/options，是同一个数据源。
      valueEnum: Object.fromEntries(
        roleOptions.map((r) => [r.value, { text: r.label }]),
      ),
      render: (_, row) => <Tag color={roleTagColor(row.role)}>{labelOf(t, ENUM_KEYS.userRole, row.role)}</Tag>,
    },
    {
      title: t('pages.userManagement.colStatus'),
      dataIndex: 'enabled',
      valueType: 'select',
      valueEnum: { true: { text: t('pages.userManagement.enable') }, false: { text: t('pages.userManagement.disable') } },
      render: (_, row) =>
        row.enabled ? <Tag color="green">{t('pages.userManagement.enable')}</Tag> : <Tag>{t('pages.userManagement.disable')}</Tag>,
    },
    {
      title: t('pages.userManagement.colDepartment'),
      dataIndex: 'departmentName',
      search: false,
      render: (_, row) => row.departmentName ?? '-',
    },
    {
      title: t('pages.userManagement.colDataScope'),
      dataIndex: 'dataScope',
      search: false,
      render: (_, row) => {
        const label = labelOf(t, ENUM_KEYS.dataScope, row.dataScope, t('pages.userManagement.dataScopeSelf'))
        return <Tag>{label}</Tag>
      },
    },
    {
      title: t('pages.userManagement.colLastLogin'),
      dataIndex: 'lastLoginAt',
      search: false,
      render: (_, row) => (row.lastLoginAt ? dayjs(row.lastLoginAt).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: t('pages.userManagement.colAction'),
      valueType: 'option',
      width: 280,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.userManagement.edit')}
        </a>,
        can[PERMS.userManage] ? (
          <a key="perm" onClick={() => void openPermission(row)}>
            {t('pages.userManagement.dataPermission')}
          </a>
        ) : null,
        can[PERMS.userManage] ? (
          <a key="reset" style={{ color: '#fa8c16' }} onClick={() => openReset(row)}>
            {t('pages.userManagement.resetPassword')}
          </a>
        ) : null,
        can[PERMS.userManage] ? (
          <Popconfirm
            key="toggle"
            title={t('pages.userManagement.toggleConfirm', { action: row.enabled ? 'pages.userManagement.toggleAction.disable' : 'pages.userManagement.toggleAction.enable', username: row.username })}
            onConfirm={() => onToggle(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{row.enabled ? t('pages.userManagement.disable') : t('pages.userManagement.enable')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<User>
        size="small"
        headerTitle={t('pages.userManagement.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
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
            {t('pages.userManagement.toolbar.newUser')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.userManagement.modal.createUser')}
        open={createOpen}
        onOk={() => void onCreate()}
        onCancel={() => setCreateOpen(false)}
        okText={t('pages.userManagement.modal.createOk')}
        destroyOnClose
      >
        <Form form={createForm} name="createUser" layout="vertical">
          <Form.Item
            name="username"
            label={t('pages.userManagement.form.username')}
            rules={[
              { required: true, message: t('pages.userManagement.form.usernameRequired') },
              { pattern: /^[a-zA-Z0-9_]{3,50}$/, message: t('pages.userManagement.form.usernamePattern') },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label={t('pages.userManagement.form.displayName')} rules={[{ required: true, message: t('pages.userManagement.form.displayNameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="role"
            label={t('pages.userManagement.form.role')}
            initialValue="SALES"
            rules={[{ required: true, message: t('pages.userManagement.form.roleRequired') }]}
          >
            <Select options={roleSelectOptions} />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('pages.userManagement.form.initialPassword')}
            rules={[
              { required: true, message: t('pages.userManagement.form.initialPasswordRequired') },
              { min: 8, max: 64, message: t('pages.userManagement.form.initialPasswordLength') },
            ]}
            extra={t('pages.userManagement.form.initialPasswordExtra')}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? t('pages.userManagement.modal.editUser', { username: editing.username }) : ''}
        open={editOpen}
        onOk={() => void onEdit()}
        onCancel={() => setEditOpen(false)}
        okText={t('pages.userManagement.modal.editOk')}
        destroyOnClose
      >
        <Form form={editForm} name="editUser" layout="vertical">
          <Form.Item name="displayName" label={t('pages.userManagement.form.displayName')} rules={[{ required: true, message: t('pages.userManagement.form.displayNameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label={t('pages.userManagement.form.role')} rules={[{ required: true, message: t('pages.userManagement.form.roleRequired') }]}>
            <Select options={roleSelectOptions} placeholder={t('pages.userManagement.form.rolePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? t('pages.userManagement.modal.resetPassword', { username: editing.username }) : ''}
        open={resetOpen}
        onOk={() => void onReset()}
        onCancel={() => setResetOpen(false)}
        okText={t('pages.userManagement.modal.resetOk')}
        destroyOnClose
      >
        <Form form={resetForm} name="resetPasswordForm" layout="vertical">
          <Form.Item
            name="newPassword"
            label={t('pages.userManagement.form.newPassword')}
            rules={[
              { required: true, message: t('pages.userManagement.form.newPasswordRequired') },
              { min: 8, max: 64, message: t('pages.userManagement.form.newPasswordLength') },
            ]}
            extra={t('pages.userManagement.form.newPasswordExtra')}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editing ? t('pages.userManagement.modal.dataPermission', { username: editing.username }) : ''}
        open={permOpen}
        onOk={() => void onPermission()}
        onCancel={() => setPermOpen(false)}
        okText={t('pages.userManagement.modal.dataPermissionOk')}
        destroyOnClose
      >
        <Form form={permForm} name="permUser" layout="vertical">
          <Form.Item name="departmentId" label={t('pages.userManagement.form.department')}>
            <Select allowClear placeholder={t('pages.userManagement.form.departmentPlaceholder')} options={deptOptions} />
          </Form.Item>
          <Form.Item name="dataScope" label={t('pages.userManagement.form.dataScope')} rules={[{ required: true, message: t('pages.userManagement.form.dataScopeRequired') }]}>
            <Select options={Object.keys(ENUM_KEYS.dataScope).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.dataScope, code) }))} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
