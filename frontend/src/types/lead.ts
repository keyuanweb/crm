import type { CustomFieldValue } from './customField'

export type LeadStatus = 'NEW' | 'WORKING' | 'QUALIFIED' | 'DISQUALIFIED'
export type LeadSource = 'WEBSITE' | 'AD' | 'EXHIBITION' | 'REFERRAL' | 'COLD_CALL' | 'OTHER'

export interface Lead {
  id: number
  name: string
  company: string
  title?: string
  phone?: string
  email?: string
  source: LeadSource
  status: LeadStatus
  score: number
  ownerId?: number
  ownerName?: string
  /** 营销活动归因（014）。 */
  campaignId?: number
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
  convertedCustomerId?: number
  convertedAt?: string
  remark?: string
  version: number
  createdAt?: string
}

export interface FollowUpBrief {
  id: number
  method: string
  content: string
  createdAt: string
}

export interface LeadDetail extends Lead {
  followUps: FollowUpBrief[]
}

export interface LeadPayload {
  name: string
  company: string
  title?: string
  phone?: string
  email?: string
  source?: string
  status?: string
  score?: number
  ownerId?: number
  /** 营销活动归因（014）。 */
  campaignId?: number
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
  remark?: string
  version?: number
}

export interface LeadListParams {
  keyword?: string
  status?: string
  source?: string
  ownerId?: number
  poolOnly?: boolean
  page: number
  pageSize: number
}

export interface ConvertPayload {
  opportunityName: string
  expectedAmount: number
  remark?: string
}

export const STATUS_COLORS: Record<LeadStatus, string> = {
  NEW: 'blue',
  WORKING: 'processing',
  QUALIFIED: 'success',
  DISQUALIFIED: 'default',
}
