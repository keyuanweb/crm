import { apiClient, type PageResult } from './apiClient'
import type { AvailableField, FieldPermission } from '../types/fieldPermission'

export async function fetchFieldPermissions(
  roleCode?: string,
  entityType?: string,
  page = 1,
  pageSize = 50,
): Promise<PageResult<FieldPermission>> {
  const { data } = await apiClient.get('/field-permissions', {
    params: { roleCode, entityType, page, pageSize },
  })
  return data.data as PageResult<FieldPermission>
}

/**
 * 该实体可配置权限的字段（内置 + 自定义，102）。
 *
 * <p>此前这里调的是 `fetchCustomFields`（自定义字段定义列表）⇒ 内置字段**在配置面上不存在**，
 * 后端的内置字段权限没有任何入口。改调本端点后两种字段同源。
 *
 * <p>后端对「没有任何可配字段的 entityType」返回 422 而不是空表（契约 §3.1：空表让「没有可配字段」
 * 与「实体名拼错了」不可区分），调用方要按失败处理，别把 422 静默当成空下拉。
 */
export async function fetchAvailableFields(
  entityType: string,
  page = 1,
  pageSize = 200,
): Promise<PageResult<AvailableField>> {
  const { data } = await apiClient.get('/field-permissions/available-fields', {
    params: { entityType, page, pageSize },
  })
  return data.data as PageResult<AvailableField>
}

/**
 * 下拉选项值：把两种字段标识编进一个字符串（`fieldId|fieldKey`，空的一侧为空串）。
 *
 * <p>为什么要编码而不是直接用 `fieldId`：内置字段**没有** `fieldId`（库里那一列是 NULL），
 * 用单个数字做键就无法把「内置 phone」与「自定义字段 5」区分开——而这正是 102 要配的两种东西。
 * 编码与解码成对放在这里（而不是页面组件里），提交时再拆开（{@link toUpsertField}），
 * 「哪种字段发哪个键」只在一处决定。
 */
export function fieldOptionValue(f: AvailableField): string {
  return `${f.fieldId ?? ''}|${f.fieldKey ?? ''}`
}

/** 拆回 upsert 请求体的两个键：自定义字段给 `fieldId`、内置字段给 `fieldKey`，另一侧为 null。 */
export function toUpsertField(value: string): { fieldId: number | null; fieldKey: string | null } {
  const [id, key] = value.split('|')
  return { fieldId: id ? Number(id) : null, fieldKey: key || null }
}

export async function upsertFieldPermission(payload: {
  roleCode: string
  entityType: string
  /** 自定义字段 id；内置字段留空（102 起 fieldId 与 fieldKey 二选一）。 */
  fieldId?: number | null
  /** 内置字段名；自定义字段留空。 */
  fieldKey?: string | null
  permission: string
}): Promise<FieldPermission> {
  const { data } = await apiClient.post('/field-permissions', payload)
  return data.data as FieldPermission
}

export async function deleteFieldPermission(id: number): Promise<void> {
  await apiClient.delete(`/field-permissions/${id}`)
}
