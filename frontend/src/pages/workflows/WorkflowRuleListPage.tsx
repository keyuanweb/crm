import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createWorkflowRule,
  deleteWorkflowRule,
  fetchWorkflowRules,
  toggleWorkflowRule,
  updateWorkflowRule,
  type WorkflowRulePayload,
} from '../../services/workflowService'
import { fetchUsers } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { ACTION_LABELS, EVENT_LABELS, type WorkflowActionType, type WorkflowEventType, type WorkflowRule } from '../../types/workflow'

interface FormValues {
  name: string
  eventType: WorkflowEventType
  conditionField?: string
  conditionValue?: string
  actionType: WorkflowActionType
  targetUserId?: number
  titleTemplate?: string
  dueDays?: number
  message?: string
  enabled: boolean
}

export default function WorkflowRuleListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<WorkflowRule | null>(null)
  const [actionType, setActionType] = useState<WorkflowActionType>('ASSIGN')
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<FormValues>()

  // 加载用户列表（动作=分配时选择目标用户，替代手输 ID）
  const loadUsers = async () => {
    try {
      const res = await fetchUsers({ page: 1, pageSize: 100 })
      setUserOptions(res.items.map((u) => ({ value: u.id, label: u.displayName || u.username })))
    } catch {
      setUserOptions([])
    }
  }

  useEffect(() => {
    void loadUsers()
  }, [])

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setActionType('ASSIGN')
    setModalOpen(true)
  }

  const openEdit = (row: WorkflowRule) => {
    setEditing(row)
    setActionType(row.actionType)
    form.setFieldsValue({
      name: row.name,
      eventType: row.eventType,
      conditionField: row.condition?.field,
      conditionValue: row.condition?.value,
      actionType: row.actionType,
      targetUserId: row.action.targetUserId as number | undefined,
      titleTemplate: row.action.titleTemplate as string | undefined,
      dueDays: row.action.dueDays as number | undefined,
      message: row.action.message as string | undefined,
      enabled: row.enabled,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const action: Record<string, unknown> = {}
    if (values.actionType === 'ASSIGN') {
      action.targetUserId = values.targetUserId
    } else if (values.actionType === 'CREATE_TASK') {
      action.titleTemplate = values.titleTemplate
      action.dueDays = values.dueDays ?? 3
    } else {
      action.message = values.message
    }
    const payload: WorkflowRulePayload = {
      name: values.name.trim(),
      eventType: values.eventType,
      actionType: values.actionType,
      action,
      enabled: values.enabled,
    }
    if (values.conditionField && values.conditionValue) {
      payload.condition = { field: values.conditionField, value: values.conditionValue }
    }
    setSaving(true)
    try {
      if (editing) {
        await updateWorkflowRule(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createWorkflowRule(payload)
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

  const onToggle = async (row: WorkflowRule) => {
    try {
      await toggleWorkflowRule(row.id)
      message.success(row.enabled ? '已停用' : '已启用')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: WorkflowRule) => {
    try {
      await deleteWorkflowRule(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<WorkflowRule>[] = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '触发事件',
      dataIndex: 'eventType',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(EVENT_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag color="blue">{EVENT_LABELS[row.eventType]}</Tag>,
    },
    {
      title: '条件',
      dataIndex: 'condition',
      search: false,
      render: (_, row) => (row.condition ? `${row.condition.field} = ${row.condition.value}` : '无条件'),
    },
    {
      title: '动作',
      dataIndex: 'actionType',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(ACTION_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag>{ACTION_LABELS[row.actionType]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="toggle" onClick={() => onToggle(row)}>
          {row.enabled ? '停用' : '启用'}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除规则「${row.name}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<WorkflowRule>
        size="small"
        headerTitle="自动化规则"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchWorkflowRules({
            keyword: params.keyword,
            eventType: params.eventType,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Link key="logs" to="/workflows/logs">
            <Button>执行日志</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增规则
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑规则' : '新增规则'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={640}
      >
        <Form
          form={form}
          name="workflowRuleForm"
          layout="horizontal"
          labelCol={{ flex: '110px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
                <Input placeholder="如：商机进入谈判阶段自动分配" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="eventType"
                label="触发事件"
                rules={[{ required: true, message: '请选择触发事件' }]}
              >
                <Select
                  placeholder="选择触发事件"
                  options={Object.entries(EVENT_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="条件（可选）" style={{ marginBottom: 0 }}>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item name="conditionField">
                      <Select
                        placeholder="条件字段"
                        allowClear
                        options={[
                          { value: 'stage', label: '阶段 stage' },
                          { value: 'source', label: '来源 source' },
                          { value: 'method', label: '方式 method' },
                        ]}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="conditionValue">
                      <Input placeholder="条件值，如 NEGOTIATING" />
                    </Form.Item>
                  </Col>
                </Row>
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="actionType"
                label="动作类型"
                rules={[{ required: true, message: '请选择动作类型' }]}
              >
                <Select
                  placeholder="选择动作类型"
                  options={Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))}
                  onChange={(v) => {
                    // 切换动作类型时清除上一类型的参数残留
                    form.setFieldsValue({ targetUserId: undefined, titleTemplate: undefined, dueDays: undefined, message: undefined })
                    setActionType(v as WorkflowActionType)
                  }}
                />
              </Form.Item>
            </Col>
            {actionType === 'ASSIGN' && (
              <Col span={24}>
                <Form.Item
                  name="targetUserId"
                  label="目标用户"
                  rules={[{ required: true, message: '请选择目标用户' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择目标用户"
                    options={userOptions}
                  />
                </Form.Item>
              </Col>
            )}
            {actionType === 'CREATE_TASK' && (
              <>
                <Col span={24}>
                  <Form.Item
                    name="titleTemplate"
                    label="标题模板"
                    rules={[{ required: true, message: '请输入标题模板' }]}
                    extra="可用 {name} 替换实体名称，如：跟进{name}"
                  >
                    <Input placeholder="如：跟进{name}" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="dueDays" label="截止天数" extra="默认 3 天">
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </>
            )}
            {actionType === 'NOTIFY' && (
              <Col span={24}>
                <Form.Item
                  name="message"
                  label="通知内容"
                  rules={[{ required: true, message: '请输入通知内容' }]}
                >
                  <Input placeholder="如：客户{name}已进入谈判阶段" />
                </Form.Item>
              </Col>
            )}
            <Col span={24}>
              <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
