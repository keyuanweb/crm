export interface FieldPermission {
  id: number
  roleCode: string
  entityType: string
  /** 自定义字段 id；内置字段配置为 null（102）。 */
  fieldId: number | null
  /** 内置字段名（属性名，如 phone）；自定义字段配置为 null（102）。 */
  fieldKey?: string | null
  /**
   * 字段中文名。**102 起后端才真正赋值**（056 的响应里这个键一直存在、值一直是 null），
   * 故旧数据可能仍为 null —— 渲染时要有兜底，不能当它必然有值。
   */
  fieldName?: string | null
  permission: 'HIDDEN' | 'READ_ONLY' | 'EDITABLE'
  createdAt?: string
}

/**
 * 一个可配置权限的字段（102，`GET /field-permissions/available-fields`）。
 *
 * <p>`fieldId` 与 `fieldKey` **恰好一个非空**，与 upsert 的请求体形状一一对应：内置字段用 `fieldKey`、
 * 自定义字段用 `fieldId`。下拉选项的值把两者编在一起（见 `FieldPermissionPage.fieldOptionValue`），
 * 提交时再拆开——这样「哪种字段发哪个键」只在一处决定。
 */
export interface AvailableField {
  fieldId: number | null
  fieldKey: string | null
  fieldName: string
  builtin: boolean
}
