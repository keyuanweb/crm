import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { fetchOpportunityStages } from '../services/opportunityStageService'
import { ENUM_KEYS } from '../constants/enumLabels'
import type { OpportunityStageDef } from '../types/opportunity'

/** 阶段字典的 React Query 键。写操作成功后 invalidate 它即可让所有消费方（看板、表单、漏斗）一起刷新。 */
export const OPPORTUNITY_STAGES_QUERY_KEY = ['opportunity-stages'] as const

/** 阶段字典的缓存时长。与后端 Caffeine 的 60s 对齐，避免前端比后端旧太久。 */
const STALE_TIME_MS = 60_000

export interface UseOpportunityStagesResult {
  /** 全部阶段，按 sortOrder 升序（含已停用）。 */
  stages: OpportunityStageDef[]
  /** 进行中（非终态）的阶段，含已停用——看板列与漏斗都用它。 */
  activeStages: OpportunityStageDef[]
  /** 可以被**新选入**的阶段（进行中且未停用）——表单下拉与拖拽目标用它。 */
  selectableStages: OpportunityStageDef[]
  isLoading: boolean
  /** 阶段展示名。 */
  stageLabel: (code: string) => string
  /** 是否终态（赢单/输单）。 */
  isTerminal: (code: string) => boolean
  /** 该阶段当前是否可被新选入。 */
  isSelectable: (code: string) => boolean
}

/**
 * 商机阶段字典（1.2）。
 *
 * <p><b>为什么集中成一个 hook</b>：阶段名是看板列头、商机表单下拉、漏斗图例、销售剧本动作模板的共同数据源。
 * 各处各拉一次接口会出现「同一个阶段在不同页面显示不同名字」以及「配置页改了、别的页面还是旧的」——集中一处
 * 后所有页面共享同一份 React Query 缓存，配置页写入后 invalidate 一次即全量刷新。
 *
 * <p><b>加载失败 / 尚未返回时不报错也不空转</b>：`stageLabel` 会退回 `ENUM_KEYS.opportunityStage` 的内建文案
 * （至少内建阶段显示正常），未知编码则原样显示编码——比显示 undefined 或空白更容易定位问题。
 */
export function useOpportunityStages(): UseOpportunityStagesResult {
  const { t } = useTranslation()
  const { data, isLoading } = useQuery({
    queryKey: OPPORTUNITY_STAGES_QUERY_KEY,
    queryFn: fetchOpportunityStages,
    staleTime: STALE_TIME_MS,
  })

  const stages = useMemo(() => data ?? [], [data])

  return useMemo(() => {
    const activeStages = stages.filter((s) => s.stageType === 'ACTIVE')
    const selectableStages = activeStages.filter((s) => s.enabled === 1)
    const byCode = new Map(stages.map((s) => [s.code, s]))
    const selectableCodes = new Set(selectableStages.map((s) => s.code))

    return {
      stages,
      activeStages,
      selectableStages,
      isLoading,
      stageLabel: (code: string) => {
        const builtInKey = ENUM_KEYS.opportunityStage[code as keyof typeof ENUM_KEYS.opportunityStage]
        if (builtInKey) return t(builtInKey)
        return byCode.get(code)?.name ?? code
      },
      isTerminal: (code: string) => {
        const stage = byCode.get(code)
        // 字典还没到时不能断言「不是终态」——但也不能因此把赢单当进行中显示。退回内建编码判断，
        // 这样即便接口挂了，商机列表也不会把 CLOSED_WON 渲染成可操作的行。
        if (!stage) return code === 'CLOSED_WON' || code === 'CLOSED_LOST'
        return stage.stageType === 'WON' || stage.stageType === 'LOST'
      },
      isSelectable: (code: string) => {
        const stage = byCode.get(code)
        if (!stage) return false
        return selectableCodes.has(code)
      },
    }
  }, [stages, isLoading, t])
}
