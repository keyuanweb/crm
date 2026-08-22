import type { Contact } from './contact'

export interface Customer {
  id: number
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
  ownerId?: number
  ownerName?: string
  version: number
  createdAt?: string
}

export interface OpportunityBrief {
  id: number
  name: string
  status: string
  salesOpportunityCount: number
}

export interface FollowUpBrief {
  id: number
  method: string
  content: string
  createdAt: string
}

export interface CustomerDetail extends Customer {
  opportunities: OpportunityBrief[]
  followUps: FollowUpBrief[]
  contacts: Contact[]
}

export interface CustomerQuery {
  keyword?: string
  status?: string
  page: number
  pageSize: number
}
