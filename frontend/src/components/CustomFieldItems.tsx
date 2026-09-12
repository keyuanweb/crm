import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DatePicker, Form, Input, InputNumber, Select } from 'antd'
import { fetchFieldDefinitions } from '../services/customFieldService'
import type { CustomField, FieldEntityType, FieldType } from '../types/customField'

const VALUE_INPUTS: Record<FieldType, React.ReactNode> = {
  TEXT: <Input />,
  TEXTAREA: <Input.TextArea rows={2} />,
  NUMBER: <InputNumber style={{ width: '100%' }} />,
  DATE: <DatePicker style={{ width: '100%' }} />,
  SELECT: <Select />,
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
      {fields.map((f) => (
        <Form.Item
          key={f.id}
          name={['customFieldValues', f.id]}
          label={f.name + (f.required ? ' *' : '')}
          rules={f.required ? [{ required: true, message: t('components.customFieldItems.required', { field: f.name }) }] : undefined}
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
