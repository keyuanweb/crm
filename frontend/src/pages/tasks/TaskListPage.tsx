import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Statistic,
  Tag,
} from 'antd'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { CheckOutlined, PlusOutlined, RedoOutlined } from '@ant-design/icons'
import {
  createTask,
  deleteTask,
  fetchReminderSummary,
  fetchTasks,
  toggleTask,
  updateTask,
  type TaskPayload,
} from '../../services/taskService'
import { extractErrorMessage } from '../../services/apiClient'
import { useQuery } from '@tanstack/react-query'
import {
  LINKED_TYPE_LABELS,
  PRIORITY_COLORS,
  PRIORITY_LABELS,
  REMINDER_COLORS,
  REMINDER_LABELS,
  type LinkedType,
  type TaskItem,
  type TaskPriority,
} from '../../types/task'

interface FormValues {
  title: string
  dueAt?: Dayjs
  priority?: TaskPriority
  linkedType?: LinkedType
  linkedId?: number
  remark?: string
}

export default function TaskListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<TaskItem | null>(null)
  const [form] = Form.useForm<FormValues>()

  const summaryQuery = useQuery({
    queryKey: ['task-reminder-summary'],
    queryFn: fetchReminderSummary,
  })

  const reload = () => {
    actionRef.current?.reload()
    void summaryQuery.refetch()
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: TaskItem) => {
    setEditing(row)
    form.setFieldsValue({
      title: row.title,
      dueAt: row.dueAt ? dayjs(row.dueAt) : undefined,
      priority: row.priority,
      linkedType: row.linkedType,
      linkedId: row.linkedId,
      remark: row.remark,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: TaskPayload = {
      title: values.title.trim(),
      dueAt: values.dueAt ? values.dueAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
      priority: values.priority,
      linkedType: values.linkedType,
      linkedId: values.linkedId,
      remark: values.remark,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateTask(editing.id, { ...payload, version: editing.version })
        message.success('已保存')
      } else {
        await createTask(payload)
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

  const onToggle = async (row: TaskItem) => {
    try {
      await toggleTask(row.id)
      message.success(row.status === 'TODO' ? '已完成' : '已重开')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onDelete = async (row: TaskItem) => {
    try {
      await deleteTask(row.id)
      message.success('已删除')
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const columns: ProColumns<TaskItem>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_, row) => {
        const link = row.linkedType && row.linkedId
          ? { CUSTOMER: `/customers/${row.linkedId}`, LEAD: `/leads/${row.linkedId}`, CONTRACT: `/contracts/${row.linkedId}`, ORDER: `/orders/${row.linkedId}` }[row.linkedType]
          : null
        return link ? <Link to={link}>{row.title}</Link> : row.title
      },
    },
    {
      title: '截止时间',
      dataIndex: 'dueAt',
      search: false,
      render: (_, row) => (row.dueAt ? row.dueAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(PRIORITY_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag color={PRIORITY_COLORS[row.priority]}>{PRIORITY_LABELS[row.priority]}</Tag>,
    },
    {
      title: '提醒',
      dataIndex: 'reminderStatus',
      search: false,
      render: (_, row) => {
        const label =
          row.reminderStatus === 'OVERDUE' && row.overdueDays
            ? `逾期${row.overdueDays}天`
            : REMINDER_LABELS[row.reminderStatus]
        return <Tag color={REMINDER_COLORS[row.reminderStatus]}>{label}</Tag>
      },
    },
    {
      title: '关联',
      dataIndex: 'linkedType',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(LINKED_TYPE_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => (row.linkedType ? LINKED_TYPE_LABELS[row.linkedType] : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: { TODO: { text: '待办' }, DONE: { text: '已完成' } },
      render: (_, row) => (row.status === 'TODO' ? <Tag color="processing">待办</Tag> : <Tag color="green">已完成</Tag>),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'TODO' ? (
          <a key="done" onClick={() => onToggle(row)}>
            <CheckOutlined /> 完成
          </a>
        ) : (
          <a key="redo" onClick={() => onToggle(row)}>
            <RedoOutlined /> 重开
          </a>
        ),
        <a key="edit" onClick={() => openEdit(row)}>
          编辑
        </a>,
        <Popconfirm key="delete" title={`确定删除任务「${row.title}」吗？`} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title="待办任务" value={summaryQuery.data?.todoCount ?? 0} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title="逾期任务" value={summaryQuery.data?.overdueCount ?? 0} valueStyle={{ color: '#cf1322' }} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title="今日到期" value={summaryQuery.data?.todayCount ?? 0} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
      </Row>

      <ProTable<TaskItem>
        size="small"
        headerTitle="我的任务"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchTasks({
            keyword: params.keyword,
            status: params.status,
            priority: params.priority,
            linkedType: params.linkedType,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Link key="calendar" to="/tasks/calendar">
            <Button>日历视图</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建任务
          </Button>,
        ]}
      />

      <Modal
        title={editing ? '编辑任务' : '新建任务'}
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
          name="taskForm"
          layout="horizontal"
          labelCol={{ flex: '100px' }}
          wrapperCol={{ flex: 1 }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="dueAt" label="截止时间">
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="priority" label="优先级">
                <Select
                  options={Object.entries(PRIORITY_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="linkedType" label="关联类型">
                <Select
                  allowClear
                  placeholder="可选"
                  options={Object.entries(LINKED_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="linkedId" label="关联 ID">
                <Input placeholder="可选" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
