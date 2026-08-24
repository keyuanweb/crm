/** 外勤拜访（035）。 */
export interface FieldVisit {
  id: number
  customerId: number
  customerName: string
  theme: string
  visitTime: string
  durationMinutes?: number
  status: 'PLANNED' | 'DONE' | 'CANCELED'
  latitude?: number
  longitude?: number
  locationText?: string
  checkInTime?: string
  summary?: string
  lateFlag?: boolean
}

export interface VisitPayload {
  customerId: number
  theme: string
  visitTime: string
  durationMinutes?: number
}

export interface CheckInPayload {
  latitude?: number
  longitude?: number
  locationText?: string
  summary?: string
}

export interface VisitStats {
  items: { userId: number; userName: string; planned: number; done: number; canceled: number; completionRate: number }[]
  totalPlanned: number
  totalDone: number
  month: string
}
