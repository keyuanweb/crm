export type ExportType = 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY' | 'TICKET'
export type ExportStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface ExportJob {
  id: number
  exportType: ExportType
  status: ExportStatus
  rowCount?: number
  errorMessage?: string
  fileName?: string
  createdAt?: string
  completedAt?: string
}

export interface ExportPayload {
  exportType: string
  filter?: Record<string, unknown>
}

export const EXPORT_TYPE_LABELS: Record<ExportType, string> = {
  LEAD: '线索',
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  TICKET: '工单',
}

export const EXPORT_STATUS_LABELS: Record<ExportStatus, string> = {
  PENDING: '排队中',
  RUNNING: '生成中',
  DONE: '已完成',
  FAILED: '失败',
}
