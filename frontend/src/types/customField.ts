export type FieldEntityType = 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY' | 'TICKET'
export type FieldType = 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'DATE' | 'SELECT'

export interface CustomField {
  id: number
  entityType: FieldEntityType
  name: string
  fieldType: FieldType
  required: boolean
  options?: string
  enabled: boolean
  sortOrder?: number
  version: number
  createdAt?: string
  /**
   * 字段权限标记（056 下发，103 起前端才真正消费它）。
   *
   * 刻意声明为**可选**：`/custom-fields/definitions` 会带它（后端 `CustomFieldService.listByEntity`），
   * 而分页端点走另一个 toResponse、**不带** —— 消费点因此一律用 `?.` 兜底，缺标记 = 当可编辑
   * （与后端「未配置即 EDITABLE」的 fail-open 默认同向）。
   *
   * ⚠️ 不要与 `types/fieldPermission.ts` 的 `permission` 混为一谈：那是**配置面** API，
   * 值是 'HIDDEN' | 'READ_ONLY' | 'EDITABLE' 字符串；这个是**读面**内嵌的布尔视图。
   */
  permission?: { hidden: boolean; readOnly: boolean }
}

export interface CustomFieldPayload {
  entityType: string
  name: string
  fieldType: string
  required?: boolean
  options?: string
  sortOrder?: number
  version?: number
}

export interface CustomFieldValue {
  fieldId: number
  fieldName?: string
  value?: string
}
