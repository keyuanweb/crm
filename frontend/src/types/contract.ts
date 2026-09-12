export type ContractStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SIGNED'
  | 'EFFECTIVE'
  | 'COMPLETED'
  | 'TERMINATED'
  | 'REJECTED'

export interface ContractAttachment {
  id: number
  fileName: string
  fileSize: number
  contentType?: string
  uploadedBy?: number
  createdAt?: string
}

export interface Contract {
  id: number
  contractNo: string
  title: string
  customerId: number
  customerName?: string
  quoteId?: number
  amount: number
  startDate?: string
  endDate?: string
  content?: string
  status: ContractStatus
  approverId?: number
  approvedAt?: string
  rejectReason?: string
  effectiveAt?: string
  terminatedReason?: string
  remark?: string
  /** 续约来源/去向（046）。 */
  renewedFromId?: number
  renewedFromNo?: string
  renewedBy?: { id: number; contractNo: string; title: string }[]
  attachments?: ContractAttachment[]
  version: number
  createdAt?: string
}

export interface ContractPayload {
  title: string
  customerId: number
  quoteId?: number
  amount?: number
  startDate?: string
  endDate?: string
  content?: string
  templateId?: number
  remark?: string
  /** 续约来源合同（046）。 */
  renewedFromId?: number
  version?: number
}

export type RenewalGroup = 'EXPIRING_SOON' | 'EXPIRED_UNRENEWED' | 'RENEWED'

export interface ContractListParams {
  keyword?: string
  status?: string
  customerId?: number
  page: number
  pageSize: number
}

export interface ContractTemplate {
  id: number
  name: string
  content: string
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  createdAt?: string
}

export interface ContractTemplatePayload {
  name: string
  content: string
  status?: string
  version?: number
}

export const CONTRACT_STATUS_COLORS: Record<ContractStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'blue',
  SIGNED: 'cyan',
  EFFECTIVE: 'success',
  COMPLETED: 'green',
  TERMINATED: 'error',
  REJECTED: 'volcano',
}
