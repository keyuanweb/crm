import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { changeOwnPassword } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import { useTranslation } from 'react-i18next'

interface FormValues {
  oldPassword: string
  newPassword: string
  confirm: string
}

export default function ChangePasswordPage() {
  const { t } = useTranslation()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const clear = useAuthStore((s) => s.clear)

  const onFinish = async (values: FormValues) => {
    setError('')
    if (values.newPassword !== values.confirm) {
      setError(t('pages.changePassword.msgPasswordMismatch'))
      return
    }
    setLoading(true)
    try {
      await changeOwnPassword(values.oldPassword, values.newPassword)
      // 密码已变更，旧令牌失效，要求重新登录
      clear()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, t('pages.changePassword.msgFailed')))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 460, margin: '0 auto' }}>
      <div style={{ marginBottom: 20 }}>
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          {t('pages.changePassword.title')}
        </Typography.Title>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          {t('pages.changePassword.subtitle')}
        </Typography.Text>
      </div>
      <Card styles={{ body: { padding: '24px 28px' } }}>
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 20 }} role="alert" />}
        <Form<FormValues> name="change-password" onFinish={onFinish} layout="vertical" requiredMark={false}>
          <Form.Item
            name="oldPassword"
            label={t('pages.changePassword.currentPassword')}
            rules={[{ required: true, message: t('pages.changePassword.msgCurrentRequired') }]}
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label={t('pages.changePassword.newPassword')}
            rules={[
              { required: true, message: t('pages.changePassword.msgNewRequired') },
              { min: 8, max: 64, message: t('pages.changePassword.passwordLength') },
            ]}
            extra={t('pages.changePassword.passwordRules')}
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label={t('pages.changePassword.confirmPassword')}
            dependencies={['newPassword']}
            rules={[
              { required: true, message: t('pages.changePassword.msgConfirmRequired') },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error(t('pages.changePassword.msgPasswordMismatch')))
                },
              }),
            ]}
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 42 }}>
              {t('pages.changePassword.btnSubmit')}
            </Button>
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, marginTop: 16, textAlign: 'center' }}>
          {t('pages.changePassword.reloginNotice')}
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
