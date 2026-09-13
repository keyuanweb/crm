import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { ApprovalFlow, FlowNode } from '../../types/approval'

interface NodeRow extends FlowNode {
  key: number
}

/**
 * 审批流可选的业务类型。**只有这两类**（后端 `ApprovalFlow.businessType` 的取值）。
 *
 * <p>取值走 `ENUM_KEYS.entity` 取文案（合同 / 报价单），但**不能用 `Object.keys(ENUM_KEYS.entity)`
 * 生成选项**——那份登记表另有 LEAD / CUSTOMER / OPPORTUNITY / TICKET / ORDER 五个实体，
 * 生成出来会多出 5 个后端不认的业务类型。
 */
const BIZ_TYPES = ['CONTRACT', 'QUOTE'] as const

const APPROVER_TYPES = [
  { value: 'ROLE', label: '角色' },
  { value: 'USER', label: '指定用户' },
  { value: 'MANAGER', label: '指定用户 (上级)' },
]

export default function ApprovalFlowPage() {
  const { t } = useTranslation()
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
  // 删除审批流：DELETE /approval-flows/{id} 挂的是 workflow:manage（ApprovalController.java:66-67）。
  // 同端点族的 create / update 挂的是同一个码，但「新建」「编辑」不在收口范围内，故这里只判删除。
  const can = usePerms([PERMS.workflowManage])

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
      message.warning(t('pages.approvalFlow.msgAtLeastOneNode'))
      return
    }
    setSaving(true)
    try {
      const payload = buildPayload()
      if (editing) {
        await updateApprovalFlow(editing.id, payload)
        message.success(t('pages.approvalFlow.msgSaved'))
      } else {
        await createApprovalFlow(payload)
        message.success(t('pages.approvalFlow.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.approvalFlow.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: ApprovalFlow) => {
    try {
      await deleteApprovalFlow(row.id)
      message.success(t('pages.approvalFlow.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.approvalFlow.msgDeleteFailed')))
    }
  }

  const renderNodeEditor = (list: NodeRow[], target?: 'main' | 'extra') => (
    <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 10, background: '#fafafa' }}>
      {list.length === 0 && (
        <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 8 }}>{t('pages.approvalFlow.noNodes')}</div>
      )}
      {list.map((n) => (
        <Row key={n.key} gutter={8} style={{ marginBottom: 8 }} align="middle">
          <Col span={8}>
            <Input
              value={n.name}
              onChange={(e) => updateNode(n.key, { name: e.target.value }, target)}
              placeholder={t('pages.approvalFlow.nodePlaceholder')}
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
              placeholder={n.approverType === 'ROLE' ? t('pages.approvalFlow.roleCodePlaceholder') : t('pages.approvalFlow.userIdPlaceholder')}
            />
          </Col>
          <Col span={2}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeNode(n.key, target)} />
          </Col>
        </Row>
      ))}
      <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={() => addNode(target)} block>
        {t('pages.approvalFlow.addNode')}
      </Button>
    </div>
  )

  const columns: ProColumns<ApprovalFlow>[] = [
    { title: t('pages.approvalFlow.colName'), dataIndex: 'name' },
    {
      title: t('pages.approvalFlow.colBusinessType'),
      dataIndex: 'businessType',
      width: 100,
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.entity, row.businessType)}</Tag>,
    },
    {
      title: t('pages.approvalFlow.colNodeCount'),
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
      title: t('pages.approvalFlow.colConditionBranch'),
      search: false,
      width: 90,
      render: (_, row) => (row.conditionJson ? <Tag color="orange">{t('pages.approvalFlow.amountCondition')}</Tag> : <span>-</span>),
    },
    {
      title: t('pages.approvalFlow.colEnabled'),
      dataIndex: 'enabled',
      width: 80,
      search: false,
      render: (_, row) => <Switch checked={row.enabled} size="small" />,
    },
    {
      title: t('pages.approvalFlow.colAction'),
      valueType: 'option',
      width: 130,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.approvalFlow.edit')}
        </a>,
        can[PERMS.workflowManage] ? (
          <Popconfirm key="delete" title={t('pages.approvalFlow.confirmDelete')} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.approvalFlow.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <ProTable<ApprovalFlow>
        size="small"
        headerTitle={t('pages.approvalFlow.title')}
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
            {t('pages.approvalFlow.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.approvalFlow.modalEditTitle') : t('pages.approvalFlow.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.approvalFlow.btnSave')}
        destroyOnClose
        width={760}
      >
        <Form form={form} name="flowForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label={t('pages.approvalFlow.formNameLabel')} rules={[{ required: true, message: t('pages.approvalFlow.formNameRequired') }]} >
                <Input placeholder={t('pages.approvalFlow.formNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="businessType" label={t('pages.approvalFlow.formBusinessTypeLabel')} rules={[{ required: true, message: t('pages.approvalFlow.formBusinessTypeRequired') }]} >
                <Select options={BIZ_TYPES.map((value) => ({ value, label: labelOf(t, ENUM_KEYS.entity, value) }))} />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        <div style={{ marginBottom: 6 }}>
          <Space>
            <span style={{ fontWeight: 600, fontSize: 13 }}>{t('pages.approvalFlow.approvalNodes')}</span>
            <Switch checked={useCondition} onChange={setUseCondition} size="small" />
            <span style={{ fontSize: 13, color: '#8c8c8c' }}>{t('pages.approvalFlow.enableAmountCondition')}</span>
          </Space>
        </div>
        {renderNodeEditor(nodes)}

        {useCondition && (
          <div style={{ marginTop: 12 }}>
            <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 6 }}>
              {t('pages.approvalFlow.conditionLabel')}
            </div>
            <Space wrap style={{ marginBottom: 8 }}>
              <span>{t('pages.approvalFlow.amount')}</span>
              <Select
                value={condition.op}
                onChange={(v) => setCondition({ ...condition, op: v })}
                style={{ width: 100 }}
                options={[
                  { value: 'GT', label: t('pages.approvalFlow.gt') },
                  { value: 'GTE', label: t('pages.approvalFlow.gte') },
                  { value: 'LT', label: t('pages.approvalFlow.lt') },
                ]}
              />
              <InputNumber
                value={condition.value}
                onChange={(v) => setCondition({ ...condition, value: v ?? undefined })}
                placeholder={t('pages.approvalFlow.amountThresholdPlaceholder')}
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
