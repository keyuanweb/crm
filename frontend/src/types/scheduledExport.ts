/** 定时导出任务类型定义（079-scheduled-export）。 */

export type EntityType = 'CUSTOMER' | 'OPPORTUNITY' | 'CONTRACT' | 'ORDER' | 'INVOICE'
export type ExportFormat = 'CSV' | 'XLSX'
export type TaskStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED'
export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'EMAIL_SENT' | 'EMAIL_FAILED'

export interface ScheduledExportRequest {
  entityType: EntityType
  filterConditions?: string
  exportFormat: ExportFormat
  cronExpression: string
  periodType?: string
  hour?: number
  minute?: number
  dayOfWeek?: number
  dayOfMonth?: number
}

export interface ScheduledExportResponse {
  id: number
  userId: number
  entityType: EntityType
  filterConditions?: string
  exportFormat: ExportFormat
  cronExpression: string
  status: TaskStatus
  nextExecutionTime?: string
  createdAt?: string
  updatedAt?: string
}

export interface ScheduledExportExecutionResponse {
  id: number
  scheduledExportId: number
  executedAt: string
  status: ExecutionStatus
  filePath?: string
  fileSize?: number
  rowCount?: number
  emailStatus?: string
  errorMessage?: string
  createdAt?: string
}

export const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  CONTRACT: '合同',
  ORDER: '订单',
  INVOICE: '发票',
}

export const EXPORT_FORMAT_LABELS: Record<ExportFormat, string> = {
  CSV: 'CSV',
  XLSX: 'Excel',
}

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  ACTIVE: '活跃',
  SUSPENDED: '已暂停',
  DELETED: '已删除',
}

export const EXECUTION_STATUS_LABELS: Record<ExecutionStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  EMAIL_SENT: '邮件已发送',
  EMAIL_FAILED: '邮件发送失败',
}
