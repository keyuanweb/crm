export type TaskStatus = 'TODO' | 'DONE'
export type TaskPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type TaskReminder = 'OVERDUE' | 'TODAY' | 'NORMAL' | 'DONE'
export type LinkedType = 'CUSTOMER' | 'LEAD' | 'CONTRACT' | 'ORDER'

export interface TaskItem {
  id: number
  title: string
  dueAt?: string
  priority: TaskPriority
  status: TaskStatus
  linkedType?: LinkedType
  linkedId?: number
  remark?: string
  reminderStatus: TaskReminder
  overdueDays?: number
  version: number
  createdAt?: string
}

export interface TaskPayload {
  title: string
  dueAt?: string
  priority?: string
  linkedType?: string
  linkedId?: number
  remark?: string
  version?: number
}

export interface TaskListParams {
  keyword?: string
  status?: string
  priority?: string
  linkedType?: string
  page: number
  pageSize: number
}

export interface CalendarDay {
  date: string
  tasks: TaskItem[]
}

export interface CalendarResponse {
  month: string
  days: CalendarDay[]
}

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export const PRIORITY_COLORS: Record<TaskPriority, string> = {
  HIGH: 'red',
  MEDIUM: 'gold',
  LOW: 'default',
}

export const REMINDER_LABELS: Record<TaskReminder, string> = {
  OVERDUE: '逾期',
  TODAY: '今日到期',
  NORMAL: '正常',
  DONE: '已完成',
}

export const REMINDER_COLORS: Record<TaskReminder, string> = {
  OVERDUE: 'red',
  TODAY: 'gold',
  NORMAL: 'default',
  DONE: 'green',
}

export const LINKED_TYPE_LABELS: Record<LinkedType, string> = {
  CUSTOMER: '客户',
  LEAD: '线索',
  CONTRACT: '合同',
  ORDER: '订单',
}
