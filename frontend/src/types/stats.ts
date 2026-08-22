export interface StageStat {
  stage: string
  count: number
  amountTotal: number
}

export interface PipelineStats {
  stages: StageStat[]
  grandTotal: { count: number; amountTotal: number }
  generatedAt: string
}
