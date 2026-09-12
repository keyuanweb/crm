export interface StageActionTemplate {
  id: number
  /**
   * 阶段编码。1.2 起阶段由字典表（`/opportunity-stages`）驱动，故由联合类型放宽为 `string`——服务端
   * `StageActionTemplateService.validateStage` 用的是字典的 `activeCodes()`（非终态，含已停用）。
   * 写成联合类型的后果不是「多一个编译错误」，而是管理员新增阶段后这里的类型与真实数据悄悄不符。
   */
  stage: string
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
