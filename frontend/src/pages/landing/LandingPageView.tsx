import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { App, Button, Card, Form, Input, Result, Select, Typography } from 'antd'
import { fetchLandingPagePublic } from '../../services/landingPageService'
import { submitPublicForm } from '../../services/formService'
import { extractErrorMessage } from '../../services/apiClient'
import type { LandingPagePublic } from '../../types/landingPage'

/** 托管落地页公开渲染页（053，/lp/:id 无需登录）。 */
export default function LandingPageView() {
  const { id } = useParams<{ id: string }>()
  const { message } = App.useApp()
  const [lp, setLp] = useState<LandingPagePublic | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    void fetchLandingPagePublic(Number(id))
      .then(setLp)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [id])

  const submit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      await submitPublicForm(Number(id), { ...values })
      setDone(true)
    } catch (err) {
      message.error(extractErrorMessage(err, '提交失败'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <Card loading style={{ maxWidth: 640, margin: '40px auto' }} />
  if (error || !lp) {
    return (
      <Result
        status="404"
        title="页面不可用"
        subTitle="落地页或关联表单已停用"
        style={{ maxWidth: 640, margin: '40px auto' }}
      />
    )
  }

  const themeColor = lp.themeColor || '#1677ff'

  return (
    <div style={{ maxWidth: 640, margin: '0 auto', padding: '40px 16px' }}>
      <Card
        style={{ borderRadius: 12, borderTop: `4px solid ${themeColor}` }}
        styles={{ body: { padding: 32 } }}
      >
        <Typography.Title level={2} style={{ textAlign: 'center', color: themeColor }}>
          {lp.title}
        </Typography.Title>
        {lp.subtitle && (
          <Typography.Title level={5} type="secondary" style={{ textAlign: 'center' }}>
            {lp.subtitle}
          </Typography.Title>
        )}
        {lp.description && (
          <Typography.Paragraph style={{ textAlign: 'center', marginTop: 16 }}>
            {lp.description}
          </Typography.Paragraph>
        )}

        {done ? (
          <Result status="success" title={lp.form.successMessage || '提交成功'} />
        ) : (
          <Form form={form} layout="vertical" style={{ marginTop: 24 }}>
            {lp.form.fields.map((f) => (
              <Form.Item
                key={f.field}
                name={f.field}
                label={f.label || f.field}
                rules={[{ required: !!f.required, message: `请填写${f.label || f.field}` }]}
              >
                {f.options?.length ? (
                  <Select
                    options={f.options.map((o) => ({ value: o, label: o }))}
                    placeholder={`请选择${f.label || ''}`}
                  />
                ) : (
                  <Input placeholder={`请输入${f.label || ''}`} />
                )}
              </Form.Item>
            ))}
            <Button type="primary" block size="large" loading={submitting} onClick={() => void submit()}>
              提交
            </Button>
          </Form>
        )}
      </Card>
      <Typography.Paragraph type="secondary" style={{ textAlign: 'center', marginTop: 16, fontSize: 12 }}>
        由 CRM 客户关系管理系统提供
      </Typography.Paragraph>
    </div>
  )
}
