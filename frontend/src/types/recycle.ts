/** 回收站（025）。 */
export type RecycleType = 'CUSTOMER' | 'LEAD' | 'CONTACT' | 'OPPORTUNITY'

export interface RecycleItem {
  type: RecycleType
  id: number
  name?: string
  deletedAt?: string
  deletedBy?: number
}

export interface RecycleOpRequest {
  items: { type: RecycleType; id: number }[]
}
