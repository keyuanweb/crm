import type { CustomFieldValue } from './customField'

export interface Opportunity {
  id: number
  name: string
  customerId: number
  customerName?: string
  expectedAmountMin: number
  expectedAmountMax: number
  remark?: string
  status: 'ACTIVE' | 'ARCHIVED'
  salesOpportunityCount: number
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
  version: number
  createdAt?: string
}

/**
 * 商机阶段编码。
 *
 * <p>1.2 起阶段由字典表驱动（`GET /api/v1/opportunity-stages`），编码是**运行时数据**，故此处由联合类型放宽为
 * `string`：写死联合类型的后果不是「多一个编译错误」，而是管理员在配置页新增一个阶段后类型悄悄与数据不符——
 * TypeScript 不会替我们发现「后端多了一个阶段」，只会在某处 `Record<OpportunityStage, X>` 里静默产生一个
 * `undefined`。取值一律来自字典，不要在这里重新列举。
 */
export type OpportunityStage = string

/** 阶段类型：进行中 / 赢单终态 / 输单终态。 */
export type StageType = 'ACTIVE' | 'WON' | 'LOST'

/** 阶段字典项。 */
export interface OpportunityStageDef {
  id: number
  code: string
  name: string
  sortOrder: number
  probability: number
  stageType: StageType
  /** 1 = 启用，0 = 已停用。 */
  enabled: number
  /**
   * 内建终态（CLOSED_WON / CLOSED_LOST）：不可删除、不可改编码。由服务端下发，
   * 前端不再维护第二份「哪些是内建」的清单——两份清单必然漂移。
   */
  builtIn: boolean
  version?: number
  createdAt?: string
}

export type CloseResult = 'WON' | 'LOST'

export interface SalesOpportunity {
  id: number
  opportunityId: number
  opportunityName?: string
  customerName?: string
  amount: number
  stage: OpportunityStage
  expectedCloseDate?: string
  closeResult?: CloseResult
  closedAt?: string
  version: number
  createdAt?: string
}

export interface OpportunityDetail extends Opportunity {
  salesOpportunities: SalesOpportunity[]
}

/** 金额（分）→ 元 */
export function formatAmount(amount: number | undefined | null): string {
  if (amount === undefined || amount === null) return '-'
  return (amount / 100).toLocaleString('zh-CN')
}
