export type SlaPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface SlaPolicy {
  id: number
  priority: SlaPriority
  respondHours?: number
  resolveHours?: number
  enabled: number
  version: number
  createdAt?: string
}

export interface SlaPolicyPayload {
  priority: string
  respondHours?: number
  resolveHours?: number
  enabled?: number
  version?: number
}

export interface PrioritySla {
  priority: string
  totalOpen: number
  overdue: number
  overdueRate?: number
}

export interface SlaOverview {
  totalOpen: number
  overdue: number
  overdueRate?: number
  byPriority: PrioritySla[]
}
