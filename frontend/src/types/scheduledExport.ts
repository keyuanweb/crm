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
