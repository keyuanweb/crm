import { apiClient, type PageResult } from './apiClient'
import type {
  CalendarResponse,
  TaskItem,
  TaskListParams,
  TaskPayload,
} from '../types/task'

export type { TaskListParams, TaskPayload }

export async function fetchTasks(params: TaskListParams): Promise<PageResult<TaskItem>> {
  const { data } = await apiClient.get('/tasks', { params })
  return data.data as PageResult<TaskItem>
}

export async function createTask(payload: TaskPayload): Promise<TaskItem> {
  const { data } = await apiClient.post('/tasks', payload)
  return data.data as TaskItem
}

export async function updateTask(id: number, payload: TaskPayload): Promise<TaskItem> {
  const { data } = await apiClient.put(`/tasks/${id}`, payload)
  return data.data as TaskItem
}

export async function toggleTask(id: number): Promise<TaskItem> {
  const { data } = await apiClient.post(`/tasks/${id}/toggle`)
  return data.data as TaskItem
}

export async function deleteTask(id: number): Promise<void> {
  await apiClient.delete(`/tasks/${id}`)
}

export interface ReminderSummary {
  overdueCount: number
  todayCount: number
  todoCount: number
}

export async function fetchReminderSummary(): Promise<ReminderSummary> {
  const { data } = await apiClient.get('/tasks/reminder-summary')
  return data.data as ReminderSummary
}

export async function fetchTaskCalendar(month: string): Promise<CalendarResponse> {
  const { data } = await apiClient.get('/tasks/calendar', { params: { month } })
  return data.data as CalendarResponse
}
