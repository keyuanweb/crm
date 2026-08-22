import { useEffect, useState } from 'react'
import { DatePicker, Form, Input, InputNumber, Select } from 'antd'
import dayjs from 'dayjs'
import { fetchFieldDefinitions } from '../services/customFieldService'
import type { CustomField, CustomFieldValue, FieldEntityType, FieldType } from '../types/customField'

const VALUE_INPUTS: Record<FieldType, React.ReactNode> = {
  TEXT: <Input />,
  TEXTAREA: <Input.TextArea rows={2} />,
  NUMBER: <InputNumber style={{ width: '100%' }} />,
  DATE: <DatePicker style={{ width: '100%' }} />,
  SELECT: <Select />,
}

/** 自定义字段表单项（创建/编辑弹窗内使用）。 */
export function CustomFieldFormItems({ entityType }: { entityType: FieldEntityType }) {
  const [fields, setFields] = useState<CustomField[]>([])

  useEffect(() => {
    void fetchFieldDefinitions(entityType)
      .then(setFields)
      .catch(() => setFields([]))
  }, [entityType])

  if (fields.length === 0) return null

  return (
    <>
      {fields.map((f) => (
        <Form.Item
          key={f.id}
          name={['customFieldValues', f.id]}
          label={f.name + (f.required ? ' *' : '')}
          rules={f.required ? [{ required: true, message: `请填写${f.name}` }] : undefined}
        >
          {f.fieldType === 'SELECT' ? (
            <Select
              allowClear
              options={(f.options ?? '')
                .split(',')
                .filter((o) => o.trim())
                .map((o) => ({ value: o.trim(), label: o.trim() }))}
            />
          ) : (
            VALUE_INPUTS[f.fieldType]
          )}
        </Form.Item>
      ))}
    </>
  )
}

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
    if (v.fieldId) result[String(v.fieldId)] = v.value
  }
  return result
}
