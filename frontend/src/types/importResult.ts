/** 批量导入结果（024）。 */
export interface ImportFailure {
  row: number
  message: string
}

export interface ImportResult {
  successCount: number
  failureCount: number
  failures: ImportFailure[]
}
