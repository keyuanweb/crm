export type CallDirection = 'INBOUND' | 'OUTBOUND'
export type CallResult = 'CONNECTED' | 'NO_ANSWER' | 'BUSY' | 'FAILED'

export interface CallRecord {
  id: number
  customerId?: number
  customerName?: string
  contactId?: number
  contactName?: string
  direction: CallDirection
  durationSeconds: number
  result: CallResult
  remark?: string
  recordedBy?: number
  recordedAt?: string
}

export interface CallStats {
  totalCount: number
  totalDurationSeconds: number
  avgDurationSeconds: number
  byDirection: { direction: string; count: number }[]
}

export const CALL_DIRECTION_LABELS: Record<CallDirection, string> = {
  INBOUND: '呼入',
  OUTBOUND: '呼出',
}

export const CALL_RESULT_LABELS: Record<CallResult, string> = {
  CONNECTED: '已接通',
  NO_ANSWER: '未接听',
  BUSY: '占线',
  FAILED: '失败',
}
