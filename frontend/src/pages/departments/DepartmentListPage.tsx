import { useEffect, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Tag, InputNumber, Tree } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, EyeOutlined } from '@ant-design/icons'
import {
  createDepartment,
  deleteDepartment,
  fetchDepartmentTree,
  updateDepartment,
  type DepartmentPayload,
} from '../../services/departmentService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { Department } from '../../types/department'

interface FormValues {
  name: string
  parentId?: number
  description?: string
  sortOrder?: number
}

export default function DepartmentListPage() {
  const { t } = useTranslation()
  const { message: messageApi } = App.useApp()
  const [treeData, setTreeData] = useState<Department[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailData, setDetailData] = useState<Department | null>(null)
  const [form] = Form.useForm<FormValues>()
  const [flatOptions, setFlatOptions] = useState<{ value: number; label: string }[]>([])
  const [searchValue, setSearchValue] = useState('')
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([])
  // 086：删除部门挂 `department:manage`（DepartmentController.java:71-73）。
  //
  // <p>**当下这道判据是冗余的，如实说明**：本页取数的 `GET /departments/tree`（`:48-50`）挂的是**同一个**
  // `department:manage`（1.5 把类级 `hasRole('ADMIN')` 换成了它，并把该码授给持有「部门管理」菜单的五个角色
  // ——`DepartmentController.java:26-28` 记这是本项唯一一处实质扩权）。于是"能渲染出这棵树"本身就蕴含
  // "持有该码"，而删除端点挂的也是它 ⇒ 对任何加载得出本页的人，判据恒真，**不会真的隐藏任何按钮**。
  //
  // 仍然挂上的理由：码与端点**逐字对应**（DELETE /departments/{id}），且一旦后端把读与写拆成两个码
  // （`department:read` / `department:manage`），这里的判据立刻变成有效的收窄——不挂则会在那时静默漏出。
  // 这与「读码挂在读端点上 ⇒ 不收口」（见 `QuoteDetailPage` 导出 PDF）**不是**同一种形态，故不豁免。
  //
  // <p>顺带记录一处**与权限无关的既有缺陷**（本次刻意不修，避免超出 086 范围）：本文件 `load()` 里
  // `const t = await fetchDepartmentTree()` **遮蔽了 i18n 的 `t`**，故其 catch 分支的
  // `t('pages.departmentList.msgLoadFailed')` 会对数组调用函数而抛 TypeError——加载失败时用户看不到任何提示。
  // 同一函数里 `walk(n.children, …)` 也未防 `children` 为空，`children` 缺失时同样抛错。
  const canManage = usePerms([PERMS.departmentManage])

  const load = async () => {
    setLoading(true)
    try {
      const t = await fetchDepartmentTree()
      setTreeData(t)
      const options: { value: number; label: string }[] = []
      const allKeys: React.Key[] = []
      const walk = (nodes: Department[], prefix: string) => {
        for (const n of nodes) {
          options.push({ value: n.id, label: prefix + n.name })
          if (n.children && n.children.length > 0) {
            allKeys.push(n.id)
          }
          walk(n.children, prefix + '  ')
        }
      }
      walk(t, '')
      setFlatOptions(options)
      setExpandedKeys(allKeys)
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgLoadFailed')))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filteredTreeData = useMemo(() => {
    if (!searchValue.trim()) {
      return treeData
    }
    
    const filterTree = (nodes: Department[]): Department[] => {
      return nodes.reduce<Department[]>((acc, node) => {
        const matchesSearch = node.name.toLowerCase().includes(searchValue.toLowerCase())
        const filteredChildren = filterTree(node.children)
        
        if (matchesSearch || filteredChildren.length > 0) {
          acc.push({
            ...node,
            children: filteredChildren.length > 0 ? filteredChildren : node.children,
          })
        }
        
        return acc
      }, [])
    }
    
    return filterTree(treeData)
  }, [treeData, searchValue])

  // 使用过滤后的树数据
  const displayTreeData = filteredTreeData

  // 自动展开匹配搜索的节点
  useEffect(() => {
    if (searchValue.trim()) {
      const keys: React.Key[] = []
      const findKeys = (nodes: Department[], parentKey?: React.Key) => {
        for (const node of nodes) {
          if (node.name.toLowerCase().includes(searchValue.toLowerCase())) {
            if (parentKey) keys.push(parentKey)
          }
          findKeys(node.children, node.id)
        }
      }
      findKeys(treeData)
      setExpandedKeys(keys)
    } else {
      setExpandedKeys([])
    }
  }, [searchValue, treeData])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Department) => {
    setEditing(row)
    form.setFieldsValue({ 
      name: row.name, 
      parentId: row.parentId,
      description: row.description,
      sortOrder: row.sortOrder,
    })
    setModalOpen(true)
  }

  const openDetail = (row: Department) => {
    setDetailData(row)
    setDetailOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: DepartmentPayload = { 
      name: values.name.trim(), 
      parentId: values.parentId,
      description: values.description,
      sortOrder: values.sortOrder,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateDepartment(editing.id, { ...payload, version: editing.version })
        messageApi.success(t('pages.departmentList.msgSaved'))
      } else {
        await createDepartment(payload)
        messageApi.success(t('pages.departmentList.msgCreated'))
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Department) => {
    try {
      await deleteDepartment(row.id)
      messageApi.success(t('pages.departmentList.msgDeleted'))
      void load()
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgDeleteFailed')))
    }
  }

  const renderTreeNode = (node: Department): React.ReactNode => (
    <Tree.TreeNode
      key={node.id}
      title={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>
            {(node.childCount ?? 0) > 0 ? '📁' : '📄'} {node.name}
            <Tag style={{ marginLeft: 8 }}>{node.id}</Tag>
            {(node.memberCount ?? 0) > 0 && <Tag color="blue" style={{ marginLeft: 4 }}>{node.memberCount} {t('pages.departmentList.colMembers')}</Tag>}
          </span>
          <Space>
            <Button size="small" type="link" icon={<EyeOutlined />} onClick={(e) => { e.stopPropagation(); openDetail(node) }}>
              {t('pages.departmentList.btnDetail')}
            </Button>
            <Button size="small" type="link" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); openEdit(node) }}>
              {t('pages.departmentList.btnEdit')}
            </Button>
            {canManage[PERMS.departmentManage] && (
              <Popconfirm title={t('pages.departmentList.confirmDelete', { name: node.name })} onConfirm={(e) => { if (e) onDelete(node) }}>
                <Button size="small" type="link" danger icon={<DeleteOutlined />}>
                  {t('pages.departmentList.btnDelete')}
                </Button>
              </Popconfirm>
            )}
          </Space>
        </div>
      }
    >
      {node.children && node.children.map(child => renderTreeNode(child))}
    </Tree.TreeNode>
  )

  return (
    <Card
      title={t('pages.departmentList.title')}
      loading={loading}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            {t('pages.departmentList.btnRefresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.departmentList.btnAdd')}
          </Button>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('pages.departmentList.placeholderSearch')}
          prefix={<SearchOutlined />}
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          allowClear
          style={{ maxWidth: 300 }}
        />
      </div>

      <Tree
        expandedKeys={expandedKeys}
        onExpand={setExpandedKeys}
        showLine
        style={{ minHeight: 200, width: '100%' }}
      >
        {displayTreeData.map(node => renderTreeNode(node))}
      </Tree>

      <Modal
        title={editing ? t('pages.departmentList.modalEditTitle') : t('pages.departmentList.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.departmentList.btnSave')}
        destroyOnClose
        width={640}
      >
        <Form form={form} name="departmentForm" layout="vertical">
          <Form.Item name="name" label={t('pages.departmentList.formNameLabel')} rules={[{ required: true, message: t('pages.departmentList.formNameRequired') }]}>
            <Input placeholder={t('pages.departmentList.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="parentId" label={t('pages.departmentList.formParentLabel')}>
            <Select
              allowClear
              placeholder={t('pages.departmentList.formParentPlaceholder')}
              options={flatOptions.filter((o) => o.value !== editing?.id)}
            />
          </Form.Item>
          <Form.Item name="description" label={t('pages.departmentList.formDescLabel')}>
            <Input.TextArea rows={3} placeholder={t('pages.departmentList.formDescPlaceholder')} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('pages.departmentList.formSortLabel')}>
            <InputNumber min={0} max={9999} placeholder={t('pages.departmentList.formSortPlaceholder')} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('pages.departmentList.modalDetailTitle')}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={600}
      >
        {detailData && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailName')}：</strong>{detailData.name}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailId')}：</strong>{detailData.id}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailParent')}：</strong>
              {detailData.parentId ? (
                flatOptions.find(o => o.value === detailData.parentId)?.label || detailData.parentId
              ) : (
                t('pages.departmentList.detailTopLevel')
              )}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailDesc')}：</strong>
              {detailData.description || '-'}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailSort')}：</strong>
              {detailData.sortOrder ?? 0}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailCreatedAt')}：</strong>
              {detailData.createdAt ? new Date(detailData.createdAt).toLocaleString('zh-CN') : '-'}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailCreatedBy')}：</strong>
              {detailData.createdBy || '-'}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailMembers')}：</strong>
              {detailData.memberCount ?? 0}
            </div>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('pages.departmentList.detailChildren')}：</strong>
              {detailData.childCount ?? 0}
            </div>
          </div>
        )}
      </Modal>
    </Card>
  )
}
