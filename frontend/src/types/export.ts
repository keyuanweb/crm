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
