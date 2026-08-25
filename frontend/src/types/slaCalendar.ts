export interface WorkSlot {
  start: string
  end: string
}

export interface SlaCalendarConfig {
  id: number
  workSlots: WorkSlot[]
  workDays: number[]
  holidays: string[]
  enabled: boolean
  updatedAt?: string
}
