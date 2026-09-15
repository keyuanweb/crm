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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { useQuery } from '@tanstack/react-query'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import {
  PRIORITY_COLORS,
  REMINDER_COLORS,
  type LinkedType,
  type TaskItem,
  type TaskPriority,
} from '../../types/task'
import { FormGrid, useFormMetrics } from '../../components/ui'

interface FormValues {
  title: string
  dueAt?: Dayjs
  priority?: TaskPriority
  linkedType?: LinkedType
  linkedId?: number
  remark?: string
}

/**
 * 任务优先级取值：只有高/中/低。
 * `ENUM_KEYS.priority` 是任务与工单共用的登记表（工单多一档 URGENT），直接遍历会给任务的
 * 筛选与表单塞进一个后端不认的「紧急」，故取值仍取自任务域（PRIORITY_COLORS 的键 = TaskPriority），
 * 文案照常走登记表。
 */
const TASK_PRIORITY_CODES = Object.keys(PRIORITY_COLORS) as TaskPriority[]

export default function TaskListPage() {
  const { t } = useTranslation()
  const metrics = useFormMetrics()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<TaskItem | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 完成 / 重开打的是 POST /tasks/{id}/toggle（task:update），删除打的是 DELETE /tasks/{id}
  // （task:delete）——两个码不同，分开判断。
  const can = usePerms([PERMS.taskUpdate, PERMS.taskDelete])

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
      valueEnum: Object.fromEntries(
        TASK_PRIORITY_CODES.map((code) => [code, { text: labelOf(t, ENUM_KEYS.priority, code) }]),
      ),
      render: (_, row) => (
        <Tag color={PRIORITY_COLORS[row.priority]}>{labelOf(t, ENUM_KEYS.priority, row.priority)}</Tag>
      ),
    },
    {
      title: t('pages.task.list.colRemind'),
      dataIndex: 'reminderStatus',
      search: false,
      render: (_, row) => {
        const label =
          row.reminderStatus === 'OVERDUE' && row.overdueDays
            ? t('pages.task.list.overdueDays', { days: row.overdueDays })
            : labelOf(t, ENUM_KEYS.taskReminder, row.reminderStatus)
        return <Tag color={REMINDER_COLORS[row.reminderStatus]}>{label}</Tag>
      },
    },
    {
      title: t('pages.task.list.colLinked'),
      dataIndex: 'linkedType',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.keys(ENUM_KEYS.linkedType).map((code) => [
          code,
          { text: labelOf(t, ENUM_KEYS.linkedType, code) },
        ]),
      ),
      render: (_, row) => (row.linkedType ? labelOf(t, ENUM_KEYS.linkedType, row.linkedType) : '-'),
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
        row.status === 'TODO' && can[PERMS.taskUpdate] ? (
          <a key="done" onClick={() => onToggle(row)}>
            <CheckOutlined /> {t('pages.task.list.done')}
          </a>
        ) : row.status !== 'TODO' && can[PERMS.taskUpdate] ? (
          <a key="redo" onClick={() => onToggle(row)}>
            <RedoOutlined /> {t('pages.task.list.redo')}
          </a>
        ) : null,
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.task.list.edit')}
        </a>,
        can[PERMS.taskDelete] ? (
          <Popconfirm key="delete" title={t('pages.task.list.deleteConfirm', { name: row.title })} onConfirm={() => onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.task.list.delete')}</a>
          </Popconfirm>
        ) : null,
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
          labelCol={{ flex: `${metrics.labelWidth}px` }}
          wrapperCol={{ flex: 1 }}
        >
          {/* 5 个成对字段进栅格；「备注」是整行独占的 TextArea，按使用纪律第 1 条留在栅格之外。
              原先的 `<Row gutter={16}>` + 5 个 `<Col span={12}>` + 1 个 `<Col span={24}>`
              是**写死两列、无任何断点**的。（页首三个统计块另有一组 Row/Col，那是带 `xs/md` 断点的，
              不在本条管辖内，也未改动。） */}
          <FormGrid>
            <Form.Item name="title" label={t('pages.task.list.colTitle')} rules={[{ required: true, message: t('pages.task.list.msgTitleRequired') }]}>
              <Input />
            </Form.Item>
            <Form.Item name="dueAt" label={t('pages.task.list.colDue')}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="priority" label={t('pages.task.list.colPriority')}>
              <Select
                options={TASK_PRIORITY_CODES.map((value) => ({
                  value,
                  label: labelOf(t, ENUM_KEYS.priority, value),
                }))}
              />
            </Form.Item>
            <Form.Item name="linkedType" label="Linked Type">
              <Select
                allowClear
                placeholder={t('pages.task.list.optional')}
                options={Object.keys(ENUM_KEYS.linkedType).map((value) => ({
                  value,
                  label: labelOf(t, ENUM_KEYS.linkedType, value),
                }))}
              />
            </Form.Item>
            <Form.Item name="linkedId" label="Linked ID">
              <Input placeholder={t('pages.task.list.optional')} />
            </Form.Item>
          </FormGrid>
          <Form.Item name="remark" label={t('pages.task.list.formRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
