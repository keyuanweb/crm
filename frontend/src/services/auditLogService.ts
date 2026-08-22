import { apiClient, type PageResult } from './apiClient'
import type { AuditLog } from '../types/auditLog'

export interface AuditLogQuery {
  action?: string
  entityType?: string
  actorName?: string
  page: number
  pageSize: number
}

export async function fetchAuditLogs(params: AuditLogQuery): Promise<PageResult<AuditLog>> {
  const { data } = await apiClient.get('/audit-logs', { params })
  return data.data as PageResult<AuditLog>
}
