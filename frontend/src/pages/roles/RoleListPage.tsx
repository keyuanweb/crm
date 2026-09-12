import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { PERMS } from '../../constants/permissions'

interface FormValues {
  code: string
  name: string
  description?: string
  dataScope: 'ALL' | 'DEPT' | 'SELF'
  enabled: boolean
}

export default function RoleListPage() {
  const { t } = useTranslation()
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

  const canManage = hasPerm(PERMS.roleManage, user)

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
        message.success(t('pages.roleList.msgSaved'))
      } else {
        await createRole(payload)
        message.success(t('pages.roleList.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.roleList.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Role) => {
    try {
      await deleteRole(row.id)
      message.success(t('pages.roleList.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.roleList.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<Role>[] = [
    {
      title: t('pages.roleList.colCode'),
      dataIndex: 'code',
      width: 130,
      render: (_, row) => <Tag color="blue">{row.code}</Tag>,
    },
    { title: t('pages.roleList.colName'), dataIndex: 'name' },
    {
      title: t('pages.roleList.colDescription'),
      dataIndex: 'description',
      search: false,
      ellipsis: true,
      render: (_, row) => row.description || '-',
    },
    {
      title: t('pages.roleList.colDataScope'),
      dataIndex: 'dataScope',
      width: 100,
      search: false,
      render: (_, row) => {
        const v = row.dataScope
        const map: Record<string, { l: string; c: string }> = {
          ALL: { l: t('pages.roleList.dataScopeAll'), c: 'green' },
          DEPT: { l: t('pages.roleList.dataScopeDept'), c: 'blue' },
          SELF: { l: t('pages.roleList.dataScopeSelf'), c: 'orange' },
        }
        const m = map[v] ?? { l: v, c: 'default' }
        return <Tag color={m.c}>{m.l}</Tag>
      },
    },
    {
      title: t('pages.roleList.colMenuCount'),
      dataIndex: 'menus',
      width: 80,
      search: false,
      render: (_, row) => (row.menus ?? []).length,
    },
    {
      title: t('pages.roleList.colPermissionCount'),
      dataIndex: 'permissions',
      width: 80,
      search: false,
      render: (_, row) => (row.permissions ?? []).length,
    },
    {
      title: t('pages.roleList.colStatus'),
      dataIndex: 'enabled',
      width: 80,
      search: false,
      render: (_, row) =>
        row.enabled ? (
          <Tag color="green">{t('pages.roleList.statusEnabled')}</Tag>
        ) : (
          <Tag>{t('pages.roleList.statusDisabled')}</Tag>
        ),
    },
    {
      title: t('pages.roleList.colAction'),
      valueType: 'option',
      width: 120,
      render: (_, row) =>
        canManage
          ? [
              <a key="edit" onClick={() => openEdit(row)}>
                {t('pages.roleList.btnEdit')}
              </a>,
              !row.builtIn ? (
                <Popconfirm
                  key="delete"
                  title={t('pages.roleList.deleteConfirm', { name: row.name })}
                  onConfirm={() => onDelete(row)}
                >
                  <a style={{ color: '#ff4d4f' }}>{t('pages.roleList.btnDelete')}</a>
                </Popconfirm>
              ) : (
                <span key="builtin" style={{ color: '#8c8c8c' }}>
                  {t('pages.roleList.builtinLabel')}
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
        headerTitle={t('pages.roleList.title')}
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
                  {t('pages.roleList.btnCreate')}
                </Button>,
              ]
            : []
        }
      />

      <Modal
        title={editing ? t('pages.roleList.modalEditTitle') : t('pages.roleList.modalCreateTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.roleList.btnSave')}
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
                ? t('pages.roleList.alertBuiltInAdmin')
                : t('pages.roleList.alertBuiltInOther', { name: editing.name })
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
              <Form.Item
                name="code"
                label={t('pages.roleList.formCodeLabel')}
                rules={[{ required: true, message: t('pages.roleList.formCodeRequired') }]}
              >
                <Input placeholder={t('pages.roleList.formCodePlaceholder')} disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label={t('pages.roleList.formNameLabel')}
                rules={[{ required: true, message: t('pages.roleList.formNameRequired') }]}
              >
                <Input placeholder={t('pages.roleList.formNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="dataScope"
                label={t('pages.roleList.formDataScopeLabel')}
                rules={[{ required: true }]}
                extra={t('pages.roleList.formDataScopeExtra')}
              >
                <Radio.Group>
                  <Radio value="ALL">{t('pages.roleList.dataScopeAll')}</Radio>
                  <Radio value="DEPT">{t('pages.roleList.dataScopeDept')}</Radio>
                  <Radio value="SELF">{t('pages.roleList.dataScopeSelf')}</Radio>
                </Radio.Group>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="enabled"
                label={t('pages.roleList.formEnabledLabel')}
                valuePropName="checked"
                extra={t('pages.roleList.formEnabledExtra')}
              >
                <Switch
                  checkedChildren={t('pages.roleList.switchEnabled')}
                  unCheckedChildren={t('pages.roleList.switchDisabled')}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label={t('pages.roleList.formDescriptionLabel')}>
                <Input placeholder={t('pages.roleList.formDescriptionPlaceholder')} />
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
              label: `${t('pages.roleList.tabMenusLabel')}（${checkedMenus.length}/${allMenuLeafKeys.length}）`,
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
                        {checkedMenus.length === allMenuLeafKeys.length
                          ? t('pages.roleList.btnClearAll')
                          : t('pages.roleList.btnSelectAll')}
                      </Button>
                      <Button
                        size="small"
                        type="link"
                        icon={<UndoOutlined />}
                        onClick={() => setCheckedMenus(editing?.menus ?? [])}
                      >
                        {t('pages.roleList.btnReset')}
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
              label: `${t('pages.roleList.tabPermsLabel')}（${checkedPerms.length}）`,
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
                        ? t('pages.roleList.btnClearAll')
                        : t('pages.roleList.btnSelectAll')}
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
