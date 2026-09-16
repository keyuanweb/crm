import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Badge, Button, Calendar, Card, Empty, List, Modal, Tag, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { useQuery } from '@tanstack/react-query'
import { fetchTaskCalendar } from '../../services/taskService'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { PRIORITY_COLORS, REMINDER_COLORS, type TaskItem } from '../../types/task'

export default function TaskCalendarPage() {
  const { t } = useTranslation()
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const [selectedDate, setSelectedDate] = useState<Dayjs | null>(null)

  const { data } = useQuery({
    queryKey: ['task-calendar', month],
    queryFn: () => fetchTaskCalendar(month),
  })

  const daysByDate = useMemo(() => {
    const map = new Map<string, TaskItem[]>()
    for (const day of data?.days ?? []) {
      map.set(day.date, day.tasks)
    }
    return map
  }, [data])

  const selectedTasks = selectedDate ? daysByDate.get(selectedDate.format('YYYY-MM-DD')) ?? [] : []

  const dateCellRender = (date: Dayjs) => {
    const tasks = daysByDate.get(date.format('YYYY-MM-DD')) ?? []
    if (tasks.length === 0) {
      return null
    }
    const hasOverdue = tasks.some((t) => t.reminderStatus === 'OVERDUE')
    return (
      <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {tasks.slice(0, 3).map((t) => (
          <li key={t.id} style={{ fontSize: 12 }}>
            <Badge
              status={hasOverdue ? 'error' : t.reminderStatus === 'TODAY' ? 'warning' : 'processing'}
              text={<span style={{ color: hasOverdue ? '#cf1322' : undefined }}>{t.title}</span>}
            />
          </li>
        ))}
        {tasks.length > 3 && <li style={{ fontSize: 12, color: '#8c8c8c' }}>+{tasks.length - 3} {t('pages.taskCalendar.colAction')}</li>}
      </ul>
    )
  }

  return (
    <div>
      <Link to="/tasks" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.taskCalendar.title')}
        </Button>
      </Link>

      <Card title={t('pages.taskCalendar.title')}>
        <Calendar
          cellRender={(date, info) => (info.type === 'date' ? dateCellRender(date) : null)}
          onPanelChange={(date) => setMonth(date.format('YYYY-MM'))}
          onSelect={(date) => setSelectedDate(date)}
        />
      </Card>

      <Modal
        title={selectedDate ? `${t('pages.taskCalendar.title')}：${selectedDate.format('YYYY-MM-DD')}` : t('pages.taskCalendar.title')}
        open={selectedDate !== null}
        onCancel={() => setSelectedDate(null)}
        footer={null}
        destroyOnClose
      >
        {selectedTasks.length === 0 ? (
          <Empty description={t('pages.taskCalendar.emptyDay')} />
        ) : (
          <List
            dataSource={selectedTasks}
            renderItem={(task) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <span>
                      {task.title}{' '}
                      <Tag color={REMINDER_COLORS[task.reminderStatus]}>
                        {labelOf(t, ENUM_KEYS.taskReminder, task.reminderStatus)}
                      </Tag>
                    </span>
                  }
                  description={
                    <>
                      <Typography.Text type="secondary">
                        {t('pages.taskCalendar.duePrefix')}
                        {task.dueAt ? task.dueAt.replace('T', ' ').slice(0, 16) : '-'}
                      </Typography.Text>{' '}
                      <Tag color={PRIORITY_COLORS[task.priority]}>
                        {labelOf(t, ENUM_KEYS.priority, task.priority)}
                      </Tag>
                    </>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Modal>
    </div>
  )
}
