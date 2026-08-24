/** 客户查重合并（034）。 */
export interface DuplicateItem {
  customerId: number
  name: string
  company?: string
  similarity: number
  relatedCount: number
}

export interface DuplicateGroup {
  id: number
  primaryId: number
  primaryName: string
  duplicates: DuplicateItem[]
}

export interface MergeResult {
  primaryId: number
  movedOrders: number
  movedOpportunities: number
  movedContacts: number
  movedFollowUps: number
  movedTickets: number
}
