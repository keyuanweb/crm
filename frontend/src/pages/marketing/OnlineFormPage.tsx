import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd'
import { CopyOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createForm,
  deleteForm,
  fetchForms,
  fetchSubmissions,
  toggleForm,
  updateForm,
} from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
import type { FormField, OnlineForm, Submission } from '../../types/form'

interface FieldRow extends FormField {
  key: number
}

const FIELD_TYPES = [
  { value: 'TEXT', label: '单行文本' },
  { value: 'TEL', label: '手机号' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'TEXTAREA', label: '多行文本' },
]

export default function OnlineFormPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<OnlineForm | null>(null)
  const [fields, setFields] = useState<FieldRow[]>([])
  const [form] = Form.useForm<{ name: string; successMessage?: string; source?: string }>()
  const [subDrawer, setSubDrawer] = useState<OnlineForm | null>(null)
  const [submissions, setSubmissions] = useState<Submission[]>([])
  const nextKey = useRef(1)

  const reload = () => actionRef.current?.reload()

  const addField = () => {
    setFields([...fields, { key: nextKey.current++, field: '', label: '', type: 'TEXT', required: false }])
  }

  const updateField = (key: number, patch: Partial<FieldRow>) => {
    setFields(fields.map((f) => (f.key === key ? { ...f, ...patch } : f)))
  }

  const removeField = (key: number) => {
    setFields(fields.filter((f) => f.key !== key))
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setFields([{ key: nextKey.current++, field: 'name', label: '姓名', type: 'TEXT', required: true }])
    setModalOpen(true)
  }

  const openEdit = (row: OnlineForm) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, successMessage: row.successMessage, source: row.source })
    try {
      const parsed = JSON.parse(row.fields) as FormField[]
      setFields(parsed.map((f) => ({ ...f, key: nextKey.current++ })))
    } catch {
      setFields([])
    }
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const validFields = fields.filter((f) => f.field.trim() && f.label.trim())
    if (validFields.length === 0) {
      message.warning('至少需要一个有效字段')
      return
    }
    setSaving(true)
    try {
      const payload = {
        name: values.name.trim(),
        fields: validFields.map((f) => ({ field: f.field, label: f.label, type: f.type, required: f.required })),
        successMessage: values.successMessage,
        source: values.source,
      }
      if (editing) {
        await updateForm(editing.id, payload)
        message.success('已保存')
      } else {
        await createForm(payload)
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

  const onDelete = async (row: OnlineForm) => {
    try {
      await deleteForm(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const onToggle = async (row: OnlineForm, checked: boolean) => {
    try {
      await toggleForm(row.id)
      message.success(checked ? '已启用' : '已停用')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const copyLink = (row: OnlineForm) => {
    const url = `${window.location.origin}/f/${row.id}`
    void navigator.clipboard.writeText(url)
    message.success(`外链已复制：${url}`)
  }

  const openSubmissions = async (row: OnlineForm) => {
    setSubDrawer(row)
    const res = await fetchSubmissions(row.id, { page: 1, pageSize: 50 })
    setSubmissions(res.items)
  }

  const columns: ProColumns<OnlineForm>[] = [
    { title: '表单名', dataIndex: 'name' },
    { title: '来源', dataIndex: 'source', width: 90, render: (_, row) => <Tag color="geekblue">{row.source}</Tag> },
    { title: '字段数', search: false, width: 80, render: (_, row) => (JSON.parse(row.fields) as FormField[]).length },
    { title: '提交数', dataIndex: 'submissionCount', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) => (
        <Switch checked={row.status === 'ENABLED'} size="small" onChange={(c) => void onToggle(row, c)} />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="link" onClick={() => copyLink(row)}>
          <CopyOutlined /> 外链
        </a>,
        <a key="subs" onClick={() => void openSubmissions(row)}>
          记录
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title="确定删除该表单？" onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<OnlineForm>
        size="small"
        headerTitle="在线表单"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchForms()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建表单
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑表单' : '新建表单'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={720}
      >
        <Form form={form} name="formDef" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="表单名" rules={[{ required: true, message: '请输入表单名' }]}>
                <Input placeholder="如：产品试用申请" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="source" label="线索来源">
                <Select
                  options={[
                    { value: 'WEBSITE', label: '官网' },
                    { value: 'ADVERTISEMENT', label: '广告' },
                    { value: 'EXHIBITION', label: '展会' },
                    { value: 'OTHER', label: '其他' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="successMessage" label="成功提示">
            <Input placeholder="如：已收到您的申请，我们将尽快联系您" />
          </Form.Item>
        </Form>

        <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 6 }}>表单字段（field 映射线索字段：name/company/phone/email）：</div>
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 10, background: '#fafafa' }}>
          {fields.map((f) => (
            <Row key={f.key} gutter={8} style={{ marginBottom: 8 }} align="middle">
              <Col span={5}>
                <Input value={f.field} onChange={(e) => updateField(f.key, { field: e.target.value })} placeholder="field" />
              </Col>
              <Col span={7}>
                <Input value={f.label} onChange={(e) => updateField(f.key, { label: e.target.value })} placeholder="显示名" />
              </Col>
              <Col span={6}>
                <Select value={f.type} onChange={(v) => updateField(f.key, { type: v })} style={{ width: '100%' }} options={FIELD_TYPES} />
              </Col>
              <Col span={3}>
                <Space size={4}>
                  <span style={{ fontSize: 12, color: '#8c8c8c' }}>必填</span>
                  <Switch size="small" checked={f.required} onChange={(c) => updateField(f.key, { required: c })} />
                </Space>
              </Col>
              <Col span={3}>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeField(f.key)} />
              </Col>
            </Row>
          ))}
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addField} block>
            添加字段
          </Button>
        </div>
      </Modal>

      <Drawer title={`提交记录：${subDrawer?.name ?? ''}`} open={!!subDrawer} onClose={() => setSubDrawer(null)} width={560}>
        {submissions.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#8c8c8c', padding: 40 }}>暂无提交</div>
        ) : (
          <Table<Submission>
            size="small"
            rowKey="id"
            dataSource={submissions}
            pagination={false}
            columns={[
              {
                title: '提交内容',
                dataIndex: 'payload',
                render: (v: string) => {
                  try {
                    const obj = JSON.parse(v) as Record<string, string>
                    return Object.entries(obj)
                      .map(([k, val]) => `${k}: ${val}`)
                      .join('；')
                  } catch {
                    return v
                  }
                },
              },
              { title: 'IP', dataIndex: 'clientIp', width: 120, render: (v?: string) => v || '-' },
              { title: '线索', dataIndex: 'leadId', width: 70, render: (v?: number) => (v ? `#${v}` : '-') },
              {
                title: '时间',
                dataIndex: 'createdAt',
                width: 140,
                render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
              },
            ]}
          />
        )}
      </Drawer>
    </>
  )
}
