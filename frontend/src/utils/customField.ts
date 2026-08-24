import dayjs from 'dayjs'
import type { CustomFieldValue } from '../types/customField'

/** 将表单字段值转换为提交 payload（{fieldId, value}[]）。 */
export function toCustomFieldPayload(
  values: Record<string, unknown> | undefined,
): CustomFieldValue[] | undefined {
  if (!values) return undefined
  const result: CustomFieldValue[] = []
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined || value === null || value === '') continue
    const fieldId = Number(key)
    if (Number.isNaN(fieldId)) continue
    result.push({
      fieldId,
      value: dayjs.isDayjs(value) ? value.format('YYYY-MM-DD') : String(value),
    })
  }
  return result.length > 0 ? result : undefined
}

/** 从实体响应中提取表单初始值（{customFieldValues: {fieldId: value}}）。 */
export function fromCustomFieldValues(
  values?: CustomFieldValue[],
): Record<string, string | number | undefined> | undefined {
  if (!values || values.length === 0) return undefined
  const result: Record<string, string | number | undefined> = {}
  for (const v of values) {
    if (v.value !== undefined && v.value !== null && v.value !== '') {
      result[String(v.fieldId)] = v.value
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}
