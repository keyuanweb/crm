import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { type WorkflowActionType, type WorkflowEventType, type WorkflowRule } from '../../types/workflow'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

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
  templateId?: number
  tag?: string
  enabled: boolean
}

export default function WorkflowRuleListPage() {
  const { t } = useTranslation()
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
      templateId: row.action.templateId as number | undefined,
      tag: row.action.tag as string | undefined,
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
    } else if (values.actionType === 'NOTIFY') {
      action.message = values.message
    } else if (values.actionType === 'SEND_EMAIL') {
      action.templateId = values.templateId
    } else if (values.actionType === 'ADD_TAG') {
      action.tag = values.tag
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
        message.success(t('pages.workflowRule.msgSaved'))
      } else {
        await createWorkflowRule(payload)
        message.success(t('pages.workflowRule.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.workflowRule.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onToggle = async (row: WorkflowRule) => {
    try {
      await toggleWorkflowRule(row.id)
      message.success(row.enabled ? t('pages.workflowRule.msgDisabled') : t('pages.workflowRule.msgEnabled'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.workflowRule.msgOperationFailed')))
    }
  }

  const onDelete = async (row: WorkflowRule) => {
    try {
      await deleteWorkflowRule(row.id)
      message.success(t('pages.workflowRule.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.workflowRule.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<WorkflowRule>[] = [
    { title: t('pages.workflowRule.colName'), dataIndex: 'name' },
    {
      title: t('pages.workflowRule.colEventType'),
      dataIndex: 'eventType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.workflowEvent).map((code) => [code, { text: labelOf(t, ENUM_KEYS.workflowEvent, code) }]),
      ),
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.workflowEvent, row.eventType)}</Tag>,
    },
    {
      title: t('pages.workflowRule.colCondition'),
      dataIndex: 'condition',
      search: false,
      render: (_, row) => (row.condition ? `${row.condition.field} = ${row.condition.value}` : t('pages.workflowRule.noCondition')),
    },
    {
      title: t('pages.workflowRule.colAction'),
      dataIndex: 'actionType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.workflowAction).map((code) => [code, { text: labelOf(t, ENUM_KEYS.workflowAction, code) }]),
      ),
      render: (_, row) => <Tag>{labelOf(t, ENUM_KEYS.workflowAction, row.actionType)}</Tag>,
    },
    {
      title: t('pages.workflowRule.colStatus'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.workflowRule.enabled')}</Tag> : <Tag>{t('pages.workflowRule.disabled')}</Tag>),
    },
    {
      title: t('pages.workflowRule.colActions'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        <a key="toggle" onClick={() => onToggle(row)}>
          {row.enabled ? t('pages.workflowRule.disable') : t('pages.workflowRule.enable')}
        </a>,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.workflowRule.edit')}
        </a>,
        <Popconfirm key="delete" title={t('pages.workflowRule.confirmDelete', { name: row.name })} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>{t('pages.workflowRule.delete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<WorkflowRule>
        size="small"
        headerTitle={t('pages.workflowRule.title')}
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
            <Button>{t('pages.workflowRule.btnLogs')}</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.workflowRule.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.workflowRule.modalEditTitle') : t('pages.workflowRule.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.workflowRule.btnSave')}
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
              <Form.Item name="name" label={t('pages.workflowRule.formNameLabel')} rules={[{ required: true, message: t('pages.workflowRule.formNameRequired') }]}>
                <Input placeholder={t('pages.workflowRule.formNamePlaceholder')} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="eventType"
                label={t('pages.workflowRule.formEventTypeLabel')}
                rules={[{ required: true, message: t('pages.workflowRule.formEventTypeRequired') }]}
              >
                <Select
                  placeholder={t('pages.workflowRule.formEventTypePlaceholder')}
                  options={Object.keys(ENUM_KEYS.workflowEvent).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.workflowEvent, code) }))}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label={t('pages.workflowRule.formConditionLabel')} style={{ marginBottom: 0 }}>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item name="conditionField">
                      <Select
                        placeholder={t('pages.workflowRule.formConditionFieldPlaceholder')}
                        allowClear
                        options={[
                          { value: 'stage', label: t('pages.workflowRule.conditionStage') },
                          { value: 'source', label: t('pages.workflowRule.conditionSource') },
                          { value: 'method', label: t('pages.workflowRule.conditionMethod') },
                        ]}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="conditionValue">
                      <Input placeholder={t('pages.workflowRule.formConditionValuePlaceholder')} />
                    </Form.Item>
                  </Col>
                </Row>
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="actionType"
                label={t('pages.workflowRule.formActionTypeLabel')}
                rules={[{ required: true, message: t('pages.workflowRule.formActionTypeRequired') }]}
              >
                <Select
                  placeholder={t('pages.workflowRule.formActionTypePlaceholder')}
                  options={Object.keys(ENUM_KEYS.workflowAction).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.workflowAction, code) }))}
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
                  label={t('pages.workflowRule.formTargetUserLabel')}
                  rules={[{ required: true, message: t('pages.workflowRule.formTargetUserRequired') }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('pages.workflowRule.formTargetUserPlaceholder')}
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
                    label={t('pages.workflowRule.formTitleTemplateLabel')}
                    rules={[{ required: true, message: t('pages.workflowRule.formTitleTemplateRequired') }]}
                    extra={t('pages.workflowRule.formTitleTemplateExtra')}
                  >
                    <Input placeholder={t('pages.workflowRule.formTitleTemplatePlaceholder')} />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="dueDays" label={t('pages.workflowRule.formDueDaysLabel')} extra={t('pages.workflowRule.formDueDaysExtra')}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </>
            )}
            {actionType === 'NOTIFY' && (
              <Col span={24}>
                <Form.Item
                  name="message"
                  label={t('pages.workflowRule.formMessageLabel')}
                  rules={[{ required: true, message: t('pages.workflowRule.formMessageRequired') }]}
                >
                  <Input placeholder={t('pages.workflowRule.formMessagePlaceholder')} />
                </Form.Item>
              </Col>
            )}
            {actionType === 'SEND_EMAIL' && (
              <Col span={24}>
                <Form.Item
                  name="templateId"
                  label={t('pages.workflowRule.formTemplateIdLabel')}
                  rules={[{ required: true, message: t('pages.workflowRule.formTemplateIdRequired') }]}
                  extra={t('pages.workflowRule.formTemplateIdExtraRealEmail')}
                >
                  <InputNumber min={1} style={{ width: '100%' }} placeholder={t('pages.workflowRule.formTemplateIdPlaceholder')} />
                </Form.Item>
              </Col>
            )}
            {actionType === 'ADD_TAG' && (
              <Col span={24}>
                <Form.Item
                  name="tag"
                  label={t('pages.workflowRule.formTagLabel')}
                  rules={[{ required: true, message: t('pages.workflowRule.formTagRequired') }]}
                  extra={t('pages.workflowRule.formTagExtraNeedsExisting')}
                >
                  <Input placeholder={t('pages.workflowRule.formTagPlaceholder')} />
                </Form.Item>
              </Col>
            )}
            <Col span={24}>
              <Form.Item name="enabled" label={t('pages.workflowRule.formEnabledLabel')} valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
