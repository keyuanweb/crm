/** 在线表单（036）。 */
export interface FormField {
  field: string
  label: string
  type: 'TEXT' | 'TEL' | 'EMAIL' | 'TEXTAREA'
  required: boolean
}

export interface OnlineForm {
  id: number
  name: string
  fields: string
  successMessage?: string
  source: string
  status: string
  submissionCount: number
  createdAt?: string
}

export interface FormPayload {
  name: string
  fields: FormField[]
  successMessage?: string
  source?: string
  status?: string
}

export interface Submission {
  id: number
  payload: string
  clientIp?: string
  leadId?: number
  createdAt?: string
}
