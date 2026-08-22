export type CampaignStatus = 'PLANNING' | 'RUNNING' | 'ENDED'
export type CampaignChannel = 'WEBSITE' | 'AD' | 'EXHIBITION' | 'REFERRAL' | 'EMAIL' | 'SOCIAL' | 'OTHER'

export interface MarketingCampaign {
  id: number
  name: string
  channel: CampaignChannel
  budget?: number
  cost?: number
  startDate?: string
  endDate?: string
  status: CampaignStatus
  leadCount?: number
  customerCount?: number
  version: number
  createdAt?: string
}

export interface CampaignPayload {
  name: string
  channel: string
  budget?: number
  cost?: number
  startDate?: string
  endDate?: string
  version?: number
}

export interface CampaignListParams {
  keyword?: string
  channel?: string
  status?: string
  page: number
  pageSize: number
}

export interface ChannelRoi {
  channel: CampaignChannel
  campaignCount: number
  totalCost: number
  leadCount: number
  customerCount: number
  conversionRate?: number
  estimatedRevenue: number
  roi?: number
}

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, string> = {
  PLANNING: '筹备中',
  RUNNING: '进行中',
  ENDED: '已结束',
}

export const CAMPAIGN_STATUS_COLORS: Record<CampaignStatus, string> = {
  PLANNING: 'default',
  RUNNING: 'processing',
  ENDED: 'success',
}

export const CAMPAIGN_CHANNEL_LABELS: Record<CampaignChannel, string> = {
  WEBSITE: '官网',
  AD: '广告',
  EXHIBITION: '展会',
  REFERRAL: '转介绍',
  EMAIL: '邮件',
  SOCIAL: '社交媒体',
  OTHER: '其他',
}
