import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Form, Input, Typography } from 'antd'
import {
  CheckCircleOutlined,
  LockOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { login } from '../services/authService'
import { extractErrorMessage } from '../services/apiClient'
import { useAuthStore } from '../store/authStore'

const { Title, Paragraph, Text } = Typography

interface LoginValues {
  username: string
  password: string
}

const features = [
  '客户全生命周期管理',
  '商机与销售漏斗追踪',
  '多维度数据统计分析',
  '操作审计与权限管控',
]

export default function LoginPage() {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const setTokens = useAuthStore((s) => s.setTokens)
  const setUser = useAuthStore((s) => s.setUser)

  // 登录页禁止页面滚动条，离开时恢复
  useEffect(() => {
    const prevBodyOverflow = document.body.style.overflow
    const prevHtmlOverflow = document.documentElement.style.overflow
    const prevBodyMargin = document.body.style.margin
    const prevBodyPadding = document.body.style.padding
    document.body.style.overflow = 'hidden'
    document.documentElement.style.overflow = 'hidden'
    document.body.style.margin = '0'
    document.body.style.padding = '0'
    return () => {
      document.body.style.overflow = prevBodyOverflow
      document.documentElement.style.overflow = prevHtmlOverflow
      document.body.style.margin = prevBodyMargin
      document.body.style.padding = prevBodyPadding
    }
  }, [])

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
        height: '100vh',
        overflow: 'hidden',
        background: '#f0f2f5',
      }}
    >
      {/* 左侧品牌展示区（小屏隐藏，见 index.css .login-brand） */}
      <div
        className="login-brand"
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: '64px 56px',
          background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 60%, #003eb3 100%)',
          color: '#fff',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* 装饰圆 */}
        <div
          style={{
            position: 'absolute',
            top: -120,
            right: -80,
            width: 320,
            height: 320,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.08)',
          }}
        />
        <div
          style={{
            position: 'absolute',
            bottom: -100,
            left: -60,
            width: 260,
            height: 260,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.06)',
          }}
        />

        <div style={{ position: 'relative', zIndex: 1, maxWidth: 460 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 32 }}>
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: 14,
                background: 'rgba(255,255,255,0.18)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backdropFilter: 'blur(8px)',
              }}
            >
              <TeamOutlined style={{ fontSize: 28, color: '#fff' }} />
            </div>
            <Text style={{ fontSize: 20, fontWeight: 600, color: '#fff' }}>CRM 系统</Text>
          </div>

          <Title level={2} style={{ color: '#fff', marginBottom: 16, fontWeight: 700 }}>
            客户关系管理系统
          </Title>
          <Paragraph style={{ color: 'rgba(255,255,255,0.85)', fontSize: 15, marginBottom: 40, lineHeight: 1.8 }}>
            一站式管理客户资源、销售商机与跟进记录，助力团队高效协作，提升成交转化率。
          </Paragraph>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 24px' }}>
            {features.map((f) => (
              <div key={f} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <CheckCircleOutlined style={{ color: '#91caff', fontSize: 18 }} />
                <Text style={{ color: 'rgba(255,255,255,0.92)', fontSize: 14 }}>{f}</Text>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 右侧登录表单区 */}
      <div
        style={{
          width: 480,
          maxWidth: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '40px 32px',
          background: '#fff',
          overflow: 'hidden',
        }}
      >
        <div style={{ width: '100%', maxWidth: 360 }}>
          <div style={{ textAlign: 'center', marginBottom: 32 }}>
            <Title level={3} style={{ marginBottom: 8, fontWeight: 600 }}>
              欢迎登录
            </Title>
            <Text type="secondary" style={{ fontSize: 14 }}>
              请输入您的账号信息
            </Text>
          </div>

          {error && (
            <Alert
              type="error"
              showIcon
              message={error}
              style={{ marginBottom: 20 }}
              role="alert"
            />
          )}

          <Form<LoginValues>
            name="login"
            onFinish={onFinish}
            size="large"
            layout="vertical"
            requiredMark={false}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="用户名"
                aria-label="用户名"
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
                placeholder="密码"
                aria-label="密码"
                autoComplete="current-password"
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 16 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={loading}
                style={{ height: 44, fontWeight: 500 }}
              >
                登 录
              </Button>
            </Form.Item>
          </Form>

          <div
            style={{
              marginTop: 24,
              padding: '12px 16px',
              background: '#f5f7fa',
              borderRadius: 8,
              border: '1px solid #e8e8e8',
            }}
          >
            <Text type="secondary" style={{ fontSize: 12 }}>
              演示账号：<Text strong style={{ color: '#595959' }}>admin</Text> /{' '}
              <Text strong style={{ color: '#595959' }}>admin123</Text>
            </Text>
          </div>
        </div>
      </div>
    </div>
  )
}
