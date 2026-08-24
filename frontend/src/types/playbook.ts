export interface StageActionTemplate {
  id: number
  stage: 'INITIAL_CONTACT' | 'NEGOTIATING'
  actionName: string
  description?: string
  sortOrder?: number
  required: boolean
  enabled: boolean
  version: number
  createdAt?: string
}

export interface ActionTemplatePayload {
  stage: string
  actionName: string
  description?: string
  sortOrder?: number
  required?: boolean
  version?: number
}

export interface OpportunityAction {
  templateId: number
  actionName: string
  description?: string
  required: boolean
  completed: boolean
  completedBy?: number
  completedAt?: string
}

export const PLAYBOOK_STAGE_LABELS: Record<string, string> = {
  INITIAL_CONTACT: '初步接触',
  NEGOTIATING: '谈判中',
}
