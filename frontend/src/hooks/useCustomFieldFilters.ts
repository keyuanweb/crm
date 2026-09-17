import { useQuery } from '@tanstack/react-query'
import type { ProColumns } from '@ant-design/pro-components'
import { fetchFieldDefinitions } from '../services/customFieldService'
import type { FieldEntityType } from '../types/customField'

/** 生成某实体列表的自定义字段筛选列（dataIndex 为 cf_<fieldId>，随查询参数透传，FR-S03）。 */
export function useCustomFieldFilterColumns(entityType: FieldEntityType): ProColumns[] {
  const { data } = useQuery({
    queryKey: ['custom-field-definitions', entityType],
    queryFn: () => fetchFieldDefinitions(entityType),
  })
  return (data ?? [])
    /*
      103：HIDDEN 字段不产筛选列。否则工具栏里会出现一个用户看不见、值也不下发的 cf_<id>
      条件 —— 筛出来的结果无法解释。`permission` 缺席（分页端点那种形态）⇒ 不隐藏，与后端
      「未配置即 EDITABLE」的 fail-open 默认同向。
    */
    .filter((f) => f.enabled && !f.permission?.hidden)
    .map((f) => ({
      title: f.name,
      dataIndex: `cf_${f.id}`,
      hideInTable: true,
      valueType: f.fieldType === 'SELECT' ? ('select' as const) : ('text' as const),
      valueEnum:
        f.fieldType === 'SELECT'
          ? Object.fromEntries(
              (f.options ?? '')
                .split(',')
                .filter(Boolean)
                .map((o) => [o.trim(), { text: o.trim() }]),
            )
          : undefined,
    }))
}

/** 从 ProTable 查询参数中提取 cf_<fieldId> 键值。 */
export function extractCfParams(params: Record<string, unknown>): Record<string, string> {
  return Object.fromEntries(
    Object.entries(params)
      .filter(([k]) => k.startsWith('cf_'))
      .map(([k, v]) => [k, String(v ?? '')]),
  )
}
