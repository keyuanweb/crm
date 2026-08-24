import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { App, Button, Card, Form, Input, Result, Typography } from 'antd'
import { fetchPublicFormMeta, submitPublicForm } from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
import type { FormField } from '../../types/form'

const TYPE_RULES: Record<string, { pattern?: RegExp; message?: string }> = {
  TEL: { pattern: /^[0-9+\-() ]{5,30}$/, message: '手机号格式不正确' },
  EMAIL: { pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: '邮箱格式不正确' },
}

/** 公开表单提交页（036）：/f/:id，匿名可提交。 */
export default function PublicFormPage() {
  const { id } = useParams()
  const formId = Number(id)
  const { message } = App.useApp()
  const [meta, setMeta] = useState<{ name: string; fields: FormField[]; successMessage?: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState<string | null>(null)
  const [form] = Form.useForm()

  useEffect(() => {
    if (!formId) return
    void fetchPublicFormMeta(formId)
      .then(setMeta)
      .catch(() => message.error('表单不存在或已停用'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formId])

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const result = await submitPublicForm(formId, values as Record<string, string>)
      setDone(result.message)
    } catch (err) {
      message.error(extractErrorMessage(err, '提交失败'))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div style={{ maxWidth: 480, margin: '60px auto', padding: '0 16px' }}>
        <Card style={{ borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}>
          <Result status="success" title="提交成功" subTitle={done} />
        </Card>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 480, margin: '60px auto', padding: '0 16px' }}>
      <Card
        loading={loading}
        title={meta?.name ?? '表单'}
        style={{ borderRadius: 12, boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}
      >
        {meta && (
          <Form form={form} name="publicForm" layout="vertical" onFinish={() => void onSubmit()}>
            {meta.fields.map((f) => {
              const rule = TYPE_RULES[f.type] ?? {}
              return (
                <Form.Item
                  key={f.field}
                  name={f.field}
                  label={f.label}
                  rules={[
                    { required: f.required, message: `请填写${f.label}` },
                    rule.pattern ? { pattern: rule.pattern, message: rule.message } : undefined,
                  ].filter(Boolean) as never[]}
                >
                  {f.type === 'TEXTAREA' ? (
                    <Input.TextArea rows={3} placeholder={`请输入${f.label}`} />
                  ) : (
                    <Input
                      placeholder={`请输入${f.label}`}
                      maxLength={f.type === 'TEL' ? 30 : f.type === 'EMAIL' ? 100 : 100}
                    />
                  )}
                </Form.Item>
              )
            })}
            <Button type="primary" htmlType="submit" block loading={submitting}>
              提交
            </Button>
          </Form>
        )}
        <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center', marginTop: 12, fontSize: 12 }}>
          提交即同意我们将信息用于联系您
        </Typography.Text>
      </Card>
    </div>
  )
}
