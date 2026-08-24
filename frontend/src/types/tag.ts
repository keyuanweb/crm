/** 标签与细分（031-customer-tags）。 */
export interface Tag {
  id: number
  name: string
  color?: string
  entityType: string
}

export interface TagPayload {
  name: string
  color?: string
  entityType: string
}

/** 细分条件（JSON）。 */
export interface SegmentCondition {
  logic: 'AND' | 'OR'
  filters: {
    field: 'tag' | 'amount' | 'lastFollowUpDays'
    op: 'IN' | 'GT' | 'GTE' | 'LT' | 'LTE'
    values?: string[]
    value?: number
  }[]
}

export interface Segment {
  id: number
  name: string
  description?: string
  conditions: string
  memberCount: number
}
