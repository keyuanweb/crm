import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { changeOwnPassword } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'

interface FormValues {
  oldPassword: string
  newPassword: string
  confirm: string
}

export default function ChangePasswordPage() {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const clear = useAuthStore((s) => s.clear)

  const onFinish = async (values: FormValues) => {
    setError('')
    if (values.newPassword !== values.confirm) {
      setError('两次输入的新密码不一致')
      return
    }
    setLoading(true)
    try {
      await changeOwnPassword(values.oldPassword, values.newPassword)
      // 密码已变更，旧令牌失效，要求重新登录
      clear()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, '修改失败'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 460, margin: '0 auto' }}>
      <div style={{ marginBottom: 20 }}>
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          修改密码
        </Typography.Title>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          定期更换密码可提升账号安全性
        </Typography.Text>
      </div>
      <Card style={{ borderRadius: 10 }} bodyStyle={{ padding: '24px 28px' }}>
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 20 }} role="alert" />}
        <Form<FormValues> name="change-password" onFinish={onFinish} layout="vertical" requiredMark={false}>
          <Form.Item
            name="oldPassword"
            label="旧密码"
            rules={[{ required: true, message: '请输入旧密码' }]}
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 64, message: '8~64 位' },
            ]}
            extra="须同时包含字母与数字"
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 42 }}>
              确认修改
            </Button>
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, marginTop: 16, textAlign: 'center' }}>
          修改成功后需要重新登录（旧访问令牌立即失效）
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
