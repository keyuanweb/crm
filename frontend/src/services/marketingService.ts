import { apiClient, type PageResult } from './apiClient'
import type {
  CampaignListParams,
  CampaignPayload,
  ChannelRoi,
  MarketingCampaign,
} from '../types/marketing'

export type { CampaignListParams, CampaignPayload, ChannelRoi, MarketingCampaign }

export async function fetchCampaigns(params: CampaignListParams): Promise<PageResult<MarketingCampaign>> {
  const { data } = await apiClient.get('/campaigns', { params })
  return data.data as PageResult<MarketingCampaign>
}

export async function createCampaign(payload: CampaignPayload): Promise<MarketingCampaign> {
  const { data } = await apiClient.post('/campaigns', payload)
  return data.data as MarketingCampaign
}

export async function updateCampaign(id: number, payload: CampaignPayload): Promise<MarketingCampaign> {
  const { data } = await apiClient.put(`/campaigns/${id}`, payload)
  return data.data as MarketingCampaign
}

export async function startCampaign(id: number): Promise<MarketingCampaign> {
  const { data } = await apiClient.post(`/campaigns/${id}/start`)
  return data.data as MarketingCampaign
}

export async function endCampaign(id: number): Promise<MarketingCampaign> {
  const { data } = await apiClient.post(`/campaigns/${id}/end`)
  return data.data as MarketingCampaign
}

export async function deleteCampaign(id: number): Promise<void> {
  await apiClient.delete(`/campaigns/${id}`)
}

export async function fetchChannelRoi(): Promise<ChannelRoi[]> {
  const { data } = await apiClient.get('/campaigns/channel-roi')
  return data.data as ChannelRoi[]
}
