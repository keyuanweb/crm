export interface PortalArticle {
  id: number
  category: string
  title: string
  keywords?: string
  content?: string
  updatedAt?: string
}

export interface PortalTicketResult {
  ticketId: number
  status: string
  priority: string
  createdAt: string
}

export interface PortalTicketStatus {
  ticketId: number
  status: string
  priority: string
  slaStatus?: string
  createdAt: string
  replies: { content: string; createdAt: string }[]
}
