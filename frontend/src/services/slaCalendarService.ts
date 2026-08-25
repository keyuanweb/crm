import { apiClient } from './apiClient'
import type { SlaCalendarConfig } from '../types/slaCalendar'

export async function fetchSlaCalendar(): Promise<SlaCalendarConfig | null> {
  const { data } = await apiClient.get('/sla-calendar')
  return data.data as SlaCalendarConfig | null
}

export async function updateSlaCalendar(payload: {
  workSlots: { start: string; end: string }[]
  workDays: number[]
  holidays: string[]
  enabled: boolean
}): Promise<SlaCalendarConfig> {
  const { data } = await apiClient.put('/sla-calendar', payload)
  return data.data as SlaCalendarConfig
}
