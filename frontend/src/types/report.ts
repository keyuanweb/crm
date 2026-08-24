/** 自定义报表（021）。 */
export type ReportDimension = 'SALES' | 'PRODUCT' | 'SOURCE' | 'STAGE' | 'TIME'
export type ReportMetric = 'COUNT' | 'AMOUNT'
export type ReportGranularity = 'DAY' | 'MONTH'

export interface ReportQuery {
  dimension: ReportDimension
  metric: ReportMetric
  granularity?: ReportGranularity
  startDate?: string
  endDate?: string
  stageFilter?: string
}

export interface ReportRow {
  dimensionValue: string
  count: number
  amount: number
  ratio: number
}

export interface ReportResult {
  rows: ReportRow[]
  totalCount: number
  totalAmount: number
  dimension: string
  metric: string
  granularity?: string
}
