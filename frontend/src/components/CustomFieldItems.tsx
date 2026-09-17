import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DatePicker, Form, Input, InputNumber, Select } from 'antd'
import { fetchFieldDefinitions } from '../services/customFieldService'
import type { CustomField, FieldEntityType, FieldType } from '../types/customField'

/**
 * 值输入控件。103 起改成**工厂**（原先是共享的 element 常量）：READ_ONLY 的字段要就地加
 * `disabled`，而共享实例会让一个字段的标记漏到另一个字段上。
 */
const VALUE_INPUTS: Record<FieldType, (disabled: boolean) => React.ReactNode> = {
  TEXT: (disabled) => <Input disabled={disabled} />,
  TEXTAREA: (disabled) => <Input.TextArea rows={2} disabled={disabled} />,
  NUMBER: (disabled) => <InputNumber style={{ width: '100%' }} disabled={disabled} />,
  DATE: (disabled) => <DatePicker style={{ width: '100%' }} disabled={disabled} />,
  SELECT: (disabled) => <Select disabled={disabled} />,
}

/** 自定义字段表单项（创建/编辑弹窗内使用）。 */
export function CustomFieldFormItems({ entityType }: { entityType: FieldEntityType }) {
  const { t } = useTranslation()
  const [fields, setFields] = useState<CustomField[]>([])

  useEffect(() => {
    void fetchFieldDefinitions(entityType)
      .then(setFields)
      .catch(() => setFields([]))
  }, [entityType])

  if (fields.length === 0) return null

  return (
    <>
      {/*
        103：HIDDEN 字段**整个不渲染**。渲染它只会造成两件坏事：一个用户根本看不见的必填
        空项（表单因此永远提交不了），以及创建路径上让用户往一个改不了、也看不见的字段里打字
        （后端 validateWrite 直接 422 FIELD_HIDDEN）。不渲染也不会丢值：读路径本来就不下发
        HIDDEN 字段的值，表单里没有任何东西可以回传。
      */}
      {fields
        .filter((f) => !f.permission?.hidden)
        .map((f) => {
          // 103：READ_ONLY 的控件加 disabled。⚠️ antd 的 Form 把值存在自己的 rc-field-form
          // store 里，`onFinish` / `getFieldsValue` 从那里取值 ⇒ **disabled 控件仍会提交其值**
          // （与原生 HTML 表单相反）。这正是要的：回传值与今天逐字节相同，后端的 validateWrite
          // 才判得出「未变更」而不是 422；必填校验也照样看得见它。
          const readOnly = f.permission?.readOnly ?? false
          return (
            <Form.Item
              key={f.id}
              name={['customFieldValues', f.id]}
              label={f.name + (f.required ? ' *' : '')}
              rules={f.required ? [{ required: true, message: t('components.customFieldItems.required', { field: f.name }) }] : undefined}
            >
              {f.fieldType === 'SELECT' ? (
                <Select
                  allowClear
                  disabled={readOnly}
                  options={(f.options ?? '')
                    .split(',')
                    .filter((o) => o.trim())
                    .map((o) => ({ value: o.trim(), label: o.trim() }))}
                />
              ) : (
                VALUE_INPUTS[f.fieldType](readOnly)
              )}
            </Form.Item>
          )
        })}
    </>
  )
}
