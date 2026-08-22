import { useEffect, useState } from 'react'
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Tag } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  createDepartment,
  deleteDepartment,
  fetchDepartmentTree,
  updateDepartment,
  type DepartmentPayload,
} from '../../services/departmentService'
import { extractErrorMessage } from '../../services/apiClient'
import type { Department } from '../../types/department'

interface FormValues {
  name: string
  parentId?: number
}

export default function DepartmentListPage() {
  const { message } = App.useApp()
  const [tree, setTree] = useState<Department[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [form] = Form.useForm<FormValues>()
  const [flatOptions, setFlatOptions] = useState<{ value: number; label: string }[]>([])

  const load = async () => {
    setLoading(true)
    try {
      const t = await fetchDepartmentTree()
      setTree(t)
      const options: { value: number; label: string }[] = []
      const walk = (nodes: Department[], prefix: string) => {
        for (const n of nodes) {
          options.push({ value: n.id, label: prefix + n.name })
          walk(n.children, prefix + '　')
        }
      }
      walk(t, '')
      setFlatOptions(options)
    } catch (err) {
      message.error(extractErrorMessage(err, '加载部门失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Department) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, parentId: row.parentId })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: DepartmentPayload = { name: values.name.trim(), parentId: values.parentId }
    try {
      if (editing) {
        await updateDepartment(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createDepartment(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: Department) => {
    try {
      await deleteDepartment(row.id)
      message.success('已删除')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const renderNodeActions = (node: Department) => (
    <span style={{ marginLeft: 8 }}>
      <Button size="small" type="link" icon={<EditOutlined />} onClick={() => openEdit(node)}>
        编辑
      </Button>
      <Popconfirm title={`确定删除部门「${node.name}」吗？`} onConfirm={() => onDelete(node)}>
        <Button size="small" type="link" danger icon={<DeleteOutlined />}>
          删除
        </Button>
      </Popconfirm>
    </span>
  )

  const renderRows = (nodes: Department[], level: number): React.ReactNode =>
    nodes.map((node) => (
      <div key={node.id} style={{ padding: '6px 0' }}>
        <span style={{ paddingLeft: level * 24 }}>
          {node.children.length > 0 ? '📁' : '📄'} {node.name}
        </span>
        <Tag style={{ marginLeft: 8 }}>{node.id}</Tag>
        {renderNodeActions(node)}
        {node.children.length > 0 && <div>{renderRows(node.children, level + 1)}</div>}
      </div>
    ))

  return (
    <Card
      title="部门管理"
      loading={loading}
      style={{ borderRadius: 10 }}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增部门
          </Button>
        </Space>
      }
    >
      <div style={{ minHeight: 200 }}>{renderRows(tree, 0)}</div>

      <Modal
        title={editing ? '编辑部门' : '新增部门'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} name="departmentForm" layout="vertical">
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentId" label="上级部门">
            <Select
              allowClear
              placeholder="留空为顶级部门"
              options={flatOptions.filter((o) => o.value !== editing?.id)}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
