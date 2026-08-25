export interface TicketSurvey {
  id: number
  ticketId: number
  rating: number
  comment?: string
  createdBy: number
  createdAt: string
}

export interface SurveyStats {
  sampleCount: number
  csatAverage: number
  npsScore: number
  promoter: { count: number; percent: number }
  passive: { count: number; percent: number }
  detractor: { count: number; percent: number }
}
