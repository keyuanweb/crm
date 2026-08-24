import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createStageAction,
  deleteStageAction,
  fetchStageActions,
  updateStageAction,
  type ActionTemplatePayload,
} from '../../services/playbookService'
import { extractErrorMessage } from '../../services/apiClient'
import { PLAYBOOK_STAGE_LABELS, type StageActionTemplate } from '../../types/playbook'

interface FormValues {
  stage: string
  actionName: string
  description?: string
  sortOrder?: number
  required: boolean
}

export default function StageActionTemplatePage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<StageActionTemplate | null>(null)
  const [form] = Form.useForm<FormValues>()

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ stage: 'INITIAL_CONTACT', required: false })
    setModalOpen(true)
  }

  const openEdit = (row: StageActionTemplate) => {
    setEditing(row)
    form.setFieldsValue({
      stage: row.stage,
      actionName: row.actionName,
      description: row.description,
      sortOrder: row.sortOrder,
      required: row.required,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: ActionTemplatePayload = {
      stage: values.stage,
      actionName: values.actionName.trim(),
      description: values.description,
      sortOrder: values.sortOrder,
      required: values.required,
    }
    try {
      if (editing) {
        await updateStageAction(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createStageAction(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: StageActionTemplate) => {
    try {
      await deleteStageAction(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<StageActionTemplate>[] = [
    {
      title: '阶段',
      dataIndex: 'stage',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(PLAYBOOK_STAGE_LABELS).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, row) => <Tag color="blue">{PLAYBOOK_STAGE_LABELS[row.stage] ?? row.stage}</Tag>,
    },
    { title: '动作名称', dataIndex: 'actionName' },
    { title: '描述', dataIndex: 'description', search: false },
    { title: '排序', dataIndex: 'sortOrder', search: false },
    {
      title: '必做',
      dataIndex: 'required',
      search: false,
      render: (_, row) => (row.required ? <Tag color="red">必做</Tag> : <Tag>可选</Tag>),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title={`确定删除动作「${row.actionName}」吗？`}
          onConfirm={() => onDelete(row)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<StageActionTemplate>
        headerTitle="销售 Playbook · 阶段动作模板"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchStageActions(params.stage, params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增动作
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑动作' : '新增动作'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={520}
      >
        <Form form={form} name="stageActionForm" layout="vertical">
          <Form.Item name="stage" label="阶段" rules={[{ required: true, message: '请选择阶段' }]}>
            <Select
              disabled={!!editing}
              options={Object.entries(PLAYBOOK_STAGE_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="actionName" label="动作名称" rules={[{ required: true, message: '请输入动作名称' }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <Form.Item name="sortOrder" label="排序" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="required" label="必做" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  )
}
