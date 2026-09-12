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

export const PRIORITY_COLORS: Record<TaskPriority, string> = {
  HIGH: 'red',
  MEDIUM: 'gold',
  LOW: 'default',
}

export const REMINDER_COLORS: Record<TaskReminder, string> = {
  OVERDUE: 'red',
  TODAY: 'gold',
  NORMAL: 'default',
  DONE: 'green',
}
