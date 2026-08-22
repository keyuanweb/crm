import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { login } from '../services/authService'
import { extractErrorMessage } from '../services/apiClient'
import { useAuthStore } from '../store/authStore'

interface LoginValues {
  username: string
  password: string
}

export default function LoginPage() {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const setTokens = useAuthStore((s) => s.setTokens)
  const setUser = useAuthStore((s) => s.setUser)

  const onFinish = async (values: LoginValues) => {
    setError('')
    setLoading(true)
    try {
      const res = await login(values.username, values.password)
      setTokens(res.accessToken, res.refreshToken)
      setUser(res.user)
      navigate('/', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, '登录失败，请检查用户名与密码'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f5f5',
      }}
    >
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          CRM 客户关系管理系统
        </Typography.Title>
        {error && (
          <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} role="alert" />
        )}
        <Form<LoginValues> name="login" onFinish={onFinish} size="large">
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
        <Typography.Paragraph
          type="secondary"
          style={{ fontSize: 12, textAlign: 'center', marginBottom: 0 }}
        >
          默认账号：admin / admin123
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
