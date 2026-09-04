import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        message.success(t('pages.task.list.msgSaved'))
      } else {
        await createTask(payload)
        message.success(t('pages.task.list.msgCreated'))
      }
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const onToggle = async (row: TaskItem) => {
    try {
      await toggleTask(row.id)
      message.success(row.status === 'TODO' ? t('pages.task.list.msgDone') : t('pages.task.list.msgReopened'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const onDelete = async (row: TaskItem) => {
    try {
      await deleteTask(row.id)
      message.success(t('pages.task.list.msgDeleted'))
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    }
  }

  const columns: ProColumns<TaskItem>[] = [
    {
      title: t('pages.task.list.colTitle'),
      dataIndex: 'title',
      render: (_, row) => {
        const link = row.linkedType && row.linkedId
          ? { CUSTOMER: `/customers/${row.linkedId}`, LEAD: `/leads/${row.linkedId}`, CONTRACT: `/contracts/${row.linkedId}`, ORDER: `/orders/${row.linkedId}` }[row.linkedType]
          : null
        return link ? <Link to={link}>{row.title}</Link> : row.title
      },
    },
    {
      title: t('pages.task.list.colDue'),
      dataIndex: 'dueAt',
      search: false,
      render: (_, row) => (row.dueAt ? row.dueAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.task.list.colPriority'),
      dataIndex: 'priority',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(PRIORITY_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag color={PRIORITY_COLORS[row.priority]}>{PRIORITY_LABELS[row.priority]}</Tag>,
    },
    {
      title: t('pages.task.list.colRemind'),
      dataIndex: 'reminderStatus',
      search: false,
      render: (_, row) => {
        const label =
          row.reminderStatus === 'OVERDUE' && row.overdueDays
            ? t('pages.task.list.overdueDays', { days: row.overdueDays })
            : REMINDER_LABELS[row.reminderStatus]
        return <Tag color={REMINDER_COLORS[row.reminderStatus]}>{label}</Tag>
      },
    },
    {
      title: t('pages.task.list.colLinked'),
      dataIndex: 'linkedType',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(LINKED_TYPE_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => (row.linkedType ? LINKED_TYPE_LABELS[row.linkedType] : '-'),
    },
    {
      title: t('pages.task.list.colStatus'),
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: { TODO: { text: t('pages.task.list.todo') }, DONE: { text: t('pages.task.list.doneStatus') } },
      render: (_, row) => (row.status === 'TODO' ? <Tag color="processing">{t('pages.task.list.todo')}</Tag> : <Tag color="green">{t('pages.task.list.doneStatus')}</Tag>),
    },
    {
      title: t('pages.task.list.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => [
        row.status === 'TODO' ? (
          <a key="done" onClick={() => onToggle(row)}>
            <CheckOutlined /> {t('pages.task.list.done')}
          </a>
        ) : (
          <a key="redo" onClick={() => onToggle(row)}>
            <RedoOutlined /> {t('pages.task.list.redo')}
          </a>
        ),
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.task.list.edit')}
        </a>,
        <Popconfirm key="delete" title={t('pages.task.list.deleteConfirm', { name: row.title })} onConfirm={() => onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>{t('pages.task.list.delete')}</a>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <Row gutter={[16, 16]}>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title={t('pages.task.list.todoCount')} value={summaryQuery.data?.todoCount ?? 0} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title={t('pages.task.list.overdueCount')} value={summaryQuery.data?.overdueCount ?? 0} valueStyle={{ color: '#cf1322' }} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size="small">
            <Statistic title={t('pages.task.list.todayCount')} value={summaryQuery.data?.todayCount ?? 0} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <ProTable<TaskItem>
        size="small"
        headerTitle={t('pages.task.list.title')}
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
            <Button>{t('pages.task.list.calendarView')}</Button>
          </Link>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.task.list.create')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.task.list.editModal') : t('pages.task.list.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
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
              <Form.Item name="title" label={t('pages.task.list.colTitle')} rules={[{ required: true, message: t('pages.task.list.msgTitleRequired') }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="dueAt" label={t('pages.task.list.colDue')}>
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="priority" label={t('pages.task.list.colPriority')}>
                <Select
                  options={Object.entries(PRIORITY_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="linkedType" label="Linked Type">
                <Select
                  allowClear
                  placeholder={t('pages.task.list.optional')}
                  options={Object.entries(LINKED_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="linkedId" label="Linked ID">
                <Input placeholder={t('pages.task.list.optional')} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label={t('pages.task.list.formRemark')}>
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  )
}
