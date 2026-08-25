export interface LandingPage {
  id: number
  title: string
  subtitle?: string
  description?: string
  themeColor?: string
  formId: number
  formName?: string
  enabled: boolean
  version: number
  createdAt?: string
}

export interface LandingPagePayload {
  title: string
  subtitle?: string
  description?: string
  themeColor?: string
  formId: number
  enabled?: boolean
  version?: number
}

export interface LandingPagePublic {
  id: number
  title: string
  subtitle?: string
  description?: string
  themeColor?: string
  enabled: boolean
  form: {
    id: number
    name: string
    successMessage: string
    fields: { field: string; label: string; required?: boolean; options?: string[] }[]
  }
}

export interface UtmStats {
  landingPageId: number
  total: number
  from?: string
  to?: string
  bySource: { dimension: string; count: number }[]
  byCampaign: { dimension: string; count: number }[]
}
