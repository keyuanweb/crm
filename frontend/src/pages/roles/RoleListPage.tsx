import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  Alert,
  App,
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Space,
  Switch,
  Tabs,
  Tag,
} from 'antd'
import { CheckOutlined, PlusOutlined, UndoOutlined } from '@ant-design/icons'
import {
  createRole,
  deleteRole,
  fetchMenuTree,
  fetchPermissionDefs,
  fetchRoles,
  updateRole,
} from '../../services/roleService'
import { extractErrorMessage } from '../../services/apiClient'
import type { MenuTreeNode, PermissionDefGroup, Role } from '../../types/role'
import { useAuthStore } from '../../store/authStore'
import { hasPerm } from '../../hooks/usePermission'

interface FormValues {
  code: string
  name: string
  description?: string
  dataScope: 'ALL' | 'DEPT' | 'SELF'
  enabled: boolean
}

export default function RoleListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Role | null>(null)
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([])
  const [permDefs, setPermDefs] = useState<PermissionDefGroup[]>([])
  const [checkedMenus, setCheckedMenus] = useState<string[]>([])
  const [checkedPerms, setCheckedPerms] = useState<string[]>([])
  const [form] = Form.useForm<FormValues>()

  const canManage = hasPerm('role:manage', user)

  const loadDicts = async () => {
    try {
      const [tree, defs] = await Promise.all([fetchMenuTree(), fetchPermissionDefs()])
      setMenuTree(tree)
      setPermDefs(defs)
    } catch {
      // 字典加载失败不阻塞页面
    }
  }

  useEffect(() => {
    void loadDicts()
  }, [])

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setCheckedMenus([])
    setCheckedPerms([])
    setModalOpen(true)
  }

  const openEdit = (row: Role) => {
    setEditing(row)
    form.setFieldsValue({
      code: row.code,
      name: row.name,
      description: row.description,
      dataScope: row.dataScope,
      enabled: row.enabled,
    })
    setCheckedMenus(row.menus ?? [])
    setCheckedPerms(row.permissions ?? [])
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload = {
      code: values.code.trim().toUpperCase(),
      name: values.name.trim(),
      description: values.description,
      dataScope: values.dataScope,
      enabled: values.enabled,
      menus: checkedMenus,
      permissions: checkedPerms,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateRole(editing.id, payload)
        message.success('已保存')
      } else {
        await createRole(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Role) => {
    try {
      await deleteRole(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<Role>[] = [
    { title: '编码', dataIndex: 'code', width: 130, render: (_, row) => <Tag color="blue">{row.code}</Tag> },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', search: false, ellipsis: true, render: (_, row) => row.description || '-' },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      width: 100,
      search: false,
      render: (_, row) => {
        const v = row.dataScope
        const map: Record<string, { l: string; c: string }> = {
          ALL: { l: '全部', c: 'green' },
          DEPT: { l: '本部门', c: 'blue' },
          SELF: { l: '仅本人', c: 'orange' },
        }
        const m = map[v] ?? { l: v, c: 'default' }
        return <Tag color={m.c}>{m.l}</Tag>
      },
    },
    {
      title: '菜单数',
      dataIndex: 'menus',
      width: 80,
      search: false,
      render: (_, row) => (row.menus ?? []).length,
    },
    {
      title: '权限数',
      dataIndex: 'permissions',
      width: 80,
      search: false,
      render: (_, row) => (row.permissions ?? []).length,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, row) =>
        canManage
          ? [
              <a key="edit" onClick={() => openEdit(row)}>
                编辑
              </a>,
              !row.builtIn ? (
                <Popconfirm key="delete" title={`确定删除角色「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
                  <a style={{ color: '#ff4d4f' }}>删除</a>
                </Popconfirm>
              ) : (
                <span key="builtin" style={{ color: '#8c8c8c' }}>
                  内建
                </span>
              ),
            ]
          : [],
    },
  ]

  // 所有叶子菜单 key（全选/清空用）
  const allMenuLeafKeys = menuTree.flatMap((g) => g.children.map((c) => c.key))

  const toggleAllMenus = () => {
    setCheckedMenus(checkedMenus.length === allMenuLeafKeys.length ? [] : [...allMenuLeafKeys])
  }

  const toggleMenuGroup = (group: MenuTreeNode, checked: boolean) => {
    const keys = group.children.map((c) => c.key)
    const others = checkedMenus.filter((k) => !keys.includes(k))
    setCheckedMenus(checked ? [...others, ...keys] : others)
  }

  const toggleMenu = (key: string) => {
    setCheckedMenus(
      checkedMenus.includes(key) ? checkedMenus.filter((k) => k !== key) : [...checkedMenus, key],
    )
  }

  const togglePermGroup = (group: PermissionDefGroup, checked: boolean) => {
    const codes = group.children.map((p) => p.code)
    const others = checkedPerms.filter((c) => !codes.includes(c))
    setCheckedPerms(checked ? [...others, ...codes] : others)
  }

  return (
    <>
      <ProTable<Role>
        size="small"
        headerTitle="角色管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchRoles({ page: params.current ?? 1, pageSize: params.pageSize ?? 20 })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() =>
          canManage
            ? [
                <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新增角色
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? '编辑角色' : '新增角色'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={800}
        styles={{ body: { paddingTop: 8 } }}
      >
        {/* 内建角色提示 */}
        {editing?.builtIn && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={
              editing.code === 'ADMIN'
                ? '内建角色「系统管理员」：名称/描述/数据范围可编辑；菜单与操作权限恒为全量，不可缩减。'
                : `内建角色「${editing.name}」：名称/描述/数据范围可编辑；菜单与权限继承默认配置。`
            }
          />
        )}

        <Form
          form={form}
          name="roleForm"
          layout="horizontal"
          labelCol={{ flex: '90px' }}
          wrapperCol={{ flex: 1 }}
          initialValues={{ dataScope: 'SELF', enabled: true }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
                <Input placeholder="如 REGIONAL_MGR" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
                <Input placeholder="如：区域经理" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="dataScope"
                label="数据范围"
                rules={[{ required: true }]}
                extra="决定该角色用户默认可见的数据范围"
              >
                <Radio.Group>
                  <Radio value="ALL">全部</Radio>
                  <Radio value="DEPT">本部门</Radio>
                  <Radio value="SELF">仅本人</Radio>
                </Radio.Group>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="enabled" label="启用" valuePropName="checked" extra="停用后该角色用户无法正常访问">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label="描述">
                <Input placeholder="角色职责说明，如：负责华东区域客户的跟进与商机管理" />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        {/* 菜单/权限配置：Tabs 切换减少页面占用 */}
        <Tabs
          defaultActiveKey="menus"
          size="small"
          items={[
            {
              key: 'menus',
              label: `可见菜单（${checkedMenus.length}/${allMenuLeafKeys.length}）`,
              children: (
                <div>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'flex-end',
                      marginBottom: 8,
                    }}
                  >
                    <Space size={4}>
                      <Button size="small" type="link" icon={<CheckOutlined />} onClick={toggleAllMenus}>
                        {checkedMenus.length === allMenuLeafKeys.length ? '清空全部' : '全选全部'}
                      </Button>
                      <Button
                        size="small"
                        type="link"
                        icon={<UndoOutlined />}
                        onClick={() => setCheckedMenus(editing?.menus ?? [])}
                      >
                        重置
                      </Button>
                    </Space>
                  </div>
                  {/* 菜单分组：组纵向、叶子横向排列（内容完整显示，弹窗自适应高度） */}
                  <div>
                    {menuTree.map((g) => {
                      const groupKeys = g.children.map((c) => c.key)
                      const allChecked = groupKeys.every((k) => checkedMenus.includes(k))
                      return (
                        <div key={g.title} style={{ marginBottom: 10 }}>
                          <Checkbox
                            checked={allChecked}
                            indeterminate={!allChecked && groupKeys.some((k) => checkedMenus.includes(k))}
                            onChange={(e) => toggleMenuGroup(g, e.target.checked)}
                            style={{ fontWeight: 600, fontSize: 13 }}
                          >
                            {g.title}
                          </Checkbox>
                          <div
                            style={{
                              display: 'flex',
                              flexWrap: 'wrap',
                              gap: '2px 16px',
                              marginLeft: 22,
                              marginTop: 2,
                            }}
                          >
                            {g.children.map((c) => (
                              <Checkbox
                                key={c.key}
                                checked={checkedMenus.includes(c.key)}
                                onChange={() => toggleMenu(c.key)}
                              >
                                {c.title}
                              </Checkbox>
                            ))}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              ),
            },
            {
              key: 'perms',
              label: `数据操作权限（${checkedPerms.length}）`,
              children: (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
                    <Button
                      size="small"
                      type="link"
                      icon={<CheckOutlined />}
                      onClick={() =>
                        setCheckedPerms(
                          checkedPerms.length ===
                            permDefs.flatMap((g) => g.children.map((p) => p.code)).length
                            ? []
                            : permDefs.flatMap((g) => g.children.map((p) => p.code)),
                        )
                      }
                    >
                      {checkedPerms.length ===
                      permDefs.flatMap((g) => g.children.map((p) => p.code)).length
                        ? '清空全部'
                        : '全选全部'}
                    </Button>
                  </div>
                  {/* 权限分组：网格对齐 + 组内横向排列 */}
                  <Row gutter={[16, 12]}>
                    {permDefs.map((g) => {
                      const groupCodes = g.children.map((p) => p.code)
                      const allChecked = groupCodes.every((c) => checkedPerms.includes(c))
                      return (
                        <Col key={g.title} xs={24} sm={12} md={8}>
                          <Checkbox
                            checked={allChecked}
                            indeterminate={!allChecked && groupCodes.some((c) => checkedPerms.includes(c))}
                            onChange={(e) => togglePermGroup(g, e.target.checked)}
                            style={{ fontWeight: 600, fontSize: 13, marginBottom: 2 }}
                          >
                            {g.title}
                          </Checkbox>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0 14px', minHeight: 20 }}>
                            {g.children.map((p) => (
                              <Checkbox
                                key={p.code}
                                checked={checkedPerms.includes(p.code)}
                                onChange={() =>
                                  setCheckedPerms(
                                    checkedPerms.includes(p.code)
                                      ? checkedPerms.filter((c) => c !== p.code)
                                      : [...checkedPerms, p.code],
                                  )
                                }
                              >
                                {p.label}
                              </Checkbox>
                            ))}
                          </div>
                        </Col>
                      )
                    })}
                  </Row>
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </>
  )
}
