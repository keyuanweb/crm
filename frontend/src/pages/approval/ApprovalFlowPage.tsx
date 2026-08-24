import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Tag,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createApprovalFlow,
  deleteApprovalFlow,
  fetchApprovalFlows,
  updateApprovalFlow,
} from '../../services/approvalService'
import { extractErrorMessage } from '../../services/apiClient'
import type { ApprovalFlow, FlowNode } from '../../types/approval'

interface NodeRow extends FlowNode {
  key: number
}

const BIZ_LABELS: Record<string, string> = {
  CONTRACT: '合同',
  QUOTE: '报价单',
}

const APPROVER_TYPES = [
  { value: 'ROLE', label: '角色' },
  { value: 'USER', label: '指定用户' },
  { value: 'MANAGER', label: '指定用户(上级)' },
]

export default function ApprovalFlowPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<ApprovalFlow | null>(null)
  const [form] = Form.useForm<{ name: string; businessType: string }>()
  const [nodes, setNodes] = useState<NodeRow[]>([])
  const [useCondition, setUseCondition] = useState(false)
  const [condition, setCondition] = useState<{ field: string; op: string; value?: number; extraNodes: NodeRow[] }>({
    field: 'amount',
    op: 'GT',
    extraNodes: [],
  })
  const nextKey = useRef(1)

  const reload = () => actionRef.current?.reload()

  const addNode = (target?: 'main' | 'extra') => {
    if (target === 'extra') {
      setCondition({
        ...condition,
        extraNodes: [...condition.extraNodes, { key: nextKey.current++, name: '', approverType: 'ROLE' }],
      })
    } else {
      setNodes([...nodes, { key: nextKey.current++, name: '', approverType: 'ROLE' }])
    }
  }

  const updateNode = (key: number, patch: Partial<FlowNode>, target?: 'main' | 'extra') => {
    if (target === 'extra') {
      setCondition({
        ...condition,
        extraNodes: condition.extraNodes.map((n) => (n.key === key ? { ...n, ...patch } : n)),
      })
    } else {
      setNodes(nodes.map((n) => (n.key === key ? { ...n, ...patch } : n)))
    }
  }

  const removeNode = (key: number, target?: 'main' | 'extra') => {
    if (target === 'extra') {
      setCondition({ ...condition, extraNodes: condition.extraNodes.filter((n) => n.key !== key) })
    } else {
      setNodes(nodes.filter((n) => n.key !== key))
    }
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setNodes([{ key: nextKey.current++, name: '', approverType: 'ROLE' }])
    setUseCondition(false)
    setCondition({ field: 'amount', op: 'GT', extraNodes: [] })
    setModalOpen(true)
  }

  const openEdit = (row: ApprovalFlow) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, businessType: row.businessType })
    try {
      const parsed = JSON.parse(row.nodes) as FlowNode[]
      setNodes(parsed.map((n) => ({ ...n, key: nextKey.current++ })))
    } catch {
      setNodes([])
    }
    if (row.conditionJson) {
      try {
        const cond = JSON.parse(row.conditionJson)
        setUseCondition(true)
        setCondition({
          field: cond.field ?? 'amount',
          op: cond.op ?? 'GT',
          value: cond.value,
          extraNodes: (cond.extraNodes ?? []).map((n: FlowNode) => ({ ...n, key: nextKey.current++ })),
        })
      } catch {
        setUseCondition(false)
      }
    } else {
      setUseCondition(false)
    }
    setModalOpen(true)
  }

  const buildPayload = () => {
    const values = form.getFieldsValue()
    const validNodes = nodes.filter((n) => n.name.trim())
    const payload: {
      name: string
      businessType: string
      nodes: FlowNode[]
      conditionJson?: { field: string; op: string; value: number; extraNodes: FlowNode[] }
      enabled: boolean
    } = {
      name: values.name.trim(),
      businessType: values.businessType,
      nodes: validNodes.map((n) => ({
        name: n.name,
        approverType: n.approverType,
        approverValue: n.approverValue,
      })),
      enabled: true,
    }
    if (useCondition && condition.value !== undefined && condition.extraNodes.length > 0) {
      payload.conditionJson = {
        field: condition.field,
        op: condition.op,
        value: condition.value,
        extraNodes: condition.extraNodes
          .filter((n) => n.name.trim())
          .map((n) => ({
            name: n.name,
            approverType: n.approverType,
            approverValue: n.approverValue,
          })),
      }
    }
    return payload
  }

  const onSave = async () => {
    await form.validateFields()
    if (nodes.filter((n) => n.name.trim()).length === 0) {
      message.warning('至少需要一个审批节点')
      return
    }
    setSaving(true)
    try {
      const payload = buildPayload()
      if (editing) {
        await updateApprovalFlow(editing.id, payload)
        message.success('已保存')
      } else {
        await createApprovalFlow(payload)
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

  const onDelete = async (row: ApprovalFlow) => {
    try {
      await deleteApprovalFlow(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const renderNodeEditor = (list: NodeRow[], target?: 'main' | 'extra') => (
    <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 10, background: '#fafafa' }}>
      {list.length === 0 && (
        <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 8 }}>暂无节点</div>
      )}
      {list.map((n) => (
        <Row key={n.key} gutter={8} style={{ marginBottom: 8 }} align="middle">
          <Col span={8}>
            <Input
              value={n.name}
              onChange={(e) => updateNode(n.key, { name: e.target.value }, target)}
              placeholder="节点名，如：销售经理"
            />
          </Col>
          <Col span={7}>
            <Select
              value={n.approverType}
              onChange={(v) => updateNode(n.key, { approverType: v }, target)}
              style={{ width: '100%' }}
              options={APPROVER_TYPES}
            />
          </Col>
          <Col span={7}>
            <Input
              value={n.approverValue}
              onChange={(e) => updateNode(n.key, { approverValue: e.target.value }, target)}
              placeholder={n.approverType === 'ROLE' ? '角色码，如 ADMIN' : '用户 id'}
            />
          </Col>
          <Col span={2}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeNode(n.key, target)} />
          </Col>
        </Row>
      ))}
      <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={() => addNode(target)} block>
        添加节点
      </Button>
    </div>
  )

  const columns: ProColumns<ApprovalFlow>[] = [
    { title: '流程名', dataIndex: 'name' },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 100,
      render: (_, row) => <Tag color="blue">{BIZ_LABELS[row.businessType] ?? row.businessType}</Tag>,
    },
    {
      title: '节点数',
      search: false,
      width: 80,
      render: (_, row) => {
        try {
          return (JSON.parse(row.nodes) as FlowNode[]).length
        } catch {
          return 0
        }
      },
    },
    {
      title: '条件分支',
      search: false,
      width: 90,
      render: (_, row) => (row.conditionJson ? <Tag color="orange">金额条件</Tag> : <span>-</span>),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      search: false,
      render: (_, row) => <Switch checked={row.enabled} size="small" />,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 130,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title="确定删除该流程？" onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<ApprovalFlow>
        size="small"
        headerTitle="审批流配置"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchApprovalFlows()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建审批流
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑审批流' : '新建审批流'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={760}
      >
        <Form form={form} name="flowForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="流程名" rules={[{ required: true, message: '请输入流程名' }]}>
                <Input placeholder="如：合同审批" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="businessType" label="业务类型" rules={[{ required: true, message: '请选择业务类型' }]}>
                <Select options={Object.entries(BIZ_LABELS).map(([value, label]) => ({ value, label }))} />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <div style={{ marginBottom: 6 }}>
          <Space>
            <span style={{ fontWeight: 600, fontSize: 13 }}>审批节点：</span>
            <Switch checked={useCondition} onChange={setUseCondition} size="small" />
            <span style={{ fontSize: 13, color: '#8c8c8c' }}>启用金额条件分支</span>
          </Space>
        </div>
        {renderNodeEditor(nodes)}

        {useCondition && (
          <div style={{ marginTop: 12 }}>
            <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 6 }}>
              条件（金额超阈值追加节点）：
            </div>
            <Space wrap style={{ marginBottom: 8 }}>
              <span>金额</span>
              <Select
                value={condition.op}
                onChange={(v) => setCondition({ ...condition, op: v })}
                style={{ width: 100 }}
                options={[
                  { value: 'GT', label: '大于' },
                  { value: 'GTE', label: '大于等于' },
                  { value: 'LT', label: '小于' },
                ]}
              />
              <InputNumber
                value={condition.value}
                onChange={(v) => setCondition({ ...condition, value: v ?? undefined })}
                placeholder="金额阈值"
                style={{ width: 140 }}
              />
            </Space>
            {renderNodeEditor(condition.extraNodes, 'extra')}
          </div>
        )}
      </Modal>
    </>
  )
}
