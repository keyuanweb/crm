import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Alert, Button, Form, Input, Typography } from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  LockOutlined,
  SafetyOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { fetchCaptcha, hasTokens, isMfaChallenge, login, verifyMfa } from '../services/authService'
import { extractErrorCode, extractErrorMessage } from '../services/apiClient'
import { useAuthStore } from '../store/authStore'
import type { UserInfo } from '../store/authStore'

const { Title, Paragraph, Text } = Typography

interface LoginValues {
  username: string
  password: string
  captchaCode: string
}

/** 二次验证表单。两个字段**互斥**：视图由 `mfaUseRecovery` 决定挂哪一个。 */
interface MfaValues {
  code?: string
  recoveryCode?: string
}

// 卖点四条。**表里存键、渲染时 `t()`** —— 与 `src/constants/enumLabels.ts` 同一条纪律
// （模块级常量拿不到 `useTranslation` 的 hook，故中文不能写在这里）。
const features = [
  'login.featureLifecycle',
  'login.featurePipeline',
  'login.featureAnalytics',
  'login.featureAudit',
]

export default function LoginPage() {
  const { t } = useTranslation()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [captchaId, setCaptchaId] = useState('')
  const [captchaImg, setCaptchaImg] = useState('')
  // 二次验证（082）。`mfaToken` 是"密码已经对了、但还没走完第二次验证"的**唯一**凭证，因此：
  //   · 只放在组件 state 里 —— 不进 localStorage，不进任何全局 store。刷新页面即作废（Redis 里
  //     那张票据还在，但持有它的东西没了），这是正确的：一张未完成验证的票据不该在磁盘上过夜。
  //   · 组件卸载即丢；用户点"返回上一步"也主动清掉。
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const [mfaExpiresIn, setMfaExpiresIn] = useState(0)
  const [mfaUseRecovery, setMfaUseRecovery] = useState(false)
  const navigate = useNavigate()
  const setTokens = useAuthStore((s) => s.setTokens)
  const setUser = useAuthStore((s) => s.setUser)
  const [form] = Form.useForm<LoginValues>()

  const refreshCaptcha = async () => {
    try {
      const captcha = await fetchCaptcha()
      setCaptchaId(captcha.captchaId)
      setCaptchaImg(captcha.imageBase64)
      form.setFieldValue('captchaCode', '')
    } catch {
      // 验证码拉取失败不阻断登录表单展示，用户可点击重试
      setCaptchaImg('')
    }
  }

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

  useEffect(() => {
    void refreshCaptcha()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** 走完最后一次验证：写令牌、写用户、进主界面。两个成功分支（单因素与二次验证）共用它。 */
  const completeLogin = (res: { accessToken: string; refreshToken: string; user?: UserInfo }) => {
    setTokens(res.accessToken, res.refreshToken)
    setUser(res.user ?? null)
    navigate('/', { replace: true })
  }

  const onFinish = async (values: LoginValues) => {
    setError('')
    setLoading(true)
    try {
      const res = await login(values.username, values.password, captchaId, values.captchaCode)
      if (isMfaChallenge(res)) {
        // 密码阶段到此为止。**此处绝不 setTokens** —— 这一支里根本没有令牌，写进去就是
        // localStorage 里的字符串 "undefined"（见 authService.AuthResponse 的 javadoc：
        // 那会让 isAuthenticated() 恒真，进而变成静默重定向环）。
        setMfaToken(res.mfaToken)
        setMfaExpiresIn(res.expiresIn)
        setMfaUseRecovery(false)
        return
      }
      if (!hasTokens(res)) {
        // 既没有票据也没有令牌：契约之外的状态。**不猜、不兜底**，如实报错并让用户重试。
        setError(t('login.mfaUnexpected'))
        void refreshCaptcha()
        return
      }
      completeLogin(res)
    } catch (err) {
      setError(extractErrorMessage(err, t('login.failed')))
      void refreshCaptcha()
    } finally {
      setLoading(false)
    }
  }

  const onVerifyMfa = async (values: MfaValues) => {
    if (!mfaToken) {
      return
    }
    setError('')
    setLoading(true)
    try {
      const res = await verifyMfa(
        mfaToken,
        // 二选一：视图决定传哪个字段（而不是两个都传、让后端挑），这样"用户在哪个视图下提交的"
        // 与"服务端验的是哪种凭据"始终一致，出问题时不需要对两处做推理。
        mfaUseRecovery
          ? { recoveryCode: (values.recoveryCode ?? '').trim() }
          : { code: (values.code ?? '').trim() },
      )
      if (!hasTokens(res)) {
        setError(t('login.mfaUnexpected'))
        return
      }
      completeLogin(res)
    } catch (err) {
      // 票据失效（过期 / 已被消费）要单独处理：此时**留在本视图无论输什么码都不可能成功**
      // ——票据是一次性的，它已经没了。若不识别这一条，用户会以为是自己输错了，反复重试
      // 直到怀疑码本身，而真正的解法是回上一步重新输入密码。区分靠的是错误码而不是文案。
      if (extractErrorCode(err) === 'MFA_TICKET_INVALID') {
        setMfaToken(null)
        setMfaUseRecovery(false)
        setError(t('login.mfaExpired'))
        void refreshCaptcha()
        return
      }
      // 其余（码错但还剩次数 / 已锁定 / Redis 故障）留在本视图，文案由后端给出——
      // 它比前端更清楚"还剩几次"和"还要等多久"。
      setError(extractErrorMessage(err, t('login.mfaFailed')))
    } finally {
      setLoading(false)
    }
  }

  /** 从二次验证退回密码阶段（票据留着无用，一并丢掉）。 */
  const backToPassword = () => {
    setMfaToken(null)
    setMfaUseRecovery(false)
    setError('')
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
            <Text style={{ fontSize: 20, fontWeight: 600, color: '#fff' }}>{t('login.brandTitle')}</Text>
          </div>

          <Title level={2} style={{ color: '#fff', marginBottom: 16, fontWeight: 700 }}>
            {t('login.heroTitle')}
          </Title>
          <Paragraph style={{ color: 'rgba(255,255,255,0.85)', fontSize: 15, marginBottom: 40, lineHeight: 1.8 }}>
            {t('login.heroSubtitle')}
          </Paragraph>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 24px' }}>
            {features.map((f) => (
              <div key={f} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <CheckCircleOutlined style={{ color: '#91caff', fontSize: 18 }} />
                <Text style={{ color: 'rgba(255,255,255,0.92)', fontSize: 14 }}>{t(f)}</Text>
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
              {mfaToken ? t('login.mfaTitle') : t('login.title')}
            </Title>
            <Text type="secondary" style={{ fontSize: 14 }}>
              {mfaToken ? t('login.mfaSubtitle') : t('login.subtitle')}
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

          {mfaToken ? (
            <Form<MfaValues>
              name="mfa"
              // 用 `key` 换实例，而不是持有 `useForm()` 的引用去 resetFields()：
              //   · 换模式时**必须**丢掉另一个框里已经输入的内容 —— 新实例的 store 天然是空的；
              //   · 退出二次验证视图后，下次再进来同样是空的；
              //   · 而且不必为一个"只在某一支渲染"的表单常驻一个表单实例（antd 会警告
              //     "useForm is not connected to any Form element"，那警告说的是实话）。
              key={mfaUseRecovery ? 'recovery' : 'code'}
              onFinish={onVerifyMfa}
              size="large"
              layout="vertical"
              requiredMark={false}
            >
              {mfaUseRecovery ? (
                <Form.Item
                  name="recoveryCode"
                  rules={[{ required: true, message: t('login.mfaRecoveryRequired') }]}
                >
                  <Input
                    prefix={<KeyOutlined style={{ color: '#bfbfbf' }} />}
                    placeholder={t('login.mfaRecoveryCode')}
                    aria-label={t('login.mfaRecoveryCode')}
                    autoComplete="off"
                  />
                </Form.Item>
              ) : (
                <Form.Item
                  name="code"
                  rules={[
                    { required: true, message: t('login.mfaCodeRequired') },
                    { pattern: /^[0-9]{6}$/, message: t('login.mfaCodeFormat') },
                  ]}
                >
                  <Input
                    prefix={<SafetyOutlined style={{ color: '#bfbfbf' }} />}
                    placeholder={t('login.mfaCode')}
                    aria-label={t('login.mfaCode')}
                    // 浏览器/系统把这条当作一次性口令，允许从短信或认证器自动填充
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    maxLength={6}
                    style={{ letterSpacing: 2 }}
                  />
                </Form.Item>
              )}

              {/* 票据有效期不是写死的 5 分钟：它由后端 crm.security.mfa.token-ttl-seconds 决定，
                  文案里的分钟数由响应里的 expiresIn 算出，配置改了这里跟着变。 */}
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 16 }}>
                {t('login.mfaExpiresIn', { minutes: Math.max(1, Math.ceil(mfaExpiresIn / 60)) })}
              </Text>

              <Form.Item style={{ marginBottom: 12 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  block
                  loading={loading}
                  style={{ height: 44, fontWeight: 500 }}
                >
                  {t('login.mfaSubmit')}
                </Button>
              </Form.Item>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Button type="link" style={{ padding: 0 }} onClick={backToPassword}>
                  <ArrowLeftOutlined /> {t('login.mfaBack')}
                </Button>
                <Button
                  type="link"
                  style={{ padding: 0 }}
                  onClick={() => {
                    setMfaUseRecovery(!mfaUseRecovery)
                    setError('')
                  }}
                >
                  {mfaUseRecovery ? t('login.mfaUseCode') : t('login.mfaUseRecovery')}
                </Button>
              </div>
            </Form>
          ) : (
          <Form<LoginValues>
            name="login"
            onFinish={onFinish}
            size="large"
            layout="vertical"
            requiredMark={false}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: t('login.usernameRequired') }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
                placeholder={t('login.username')}
                aria-label={t('login.username')}
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: t('login.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
                placeholder={t('login.password')}
                aria-label={t('login.password')}
                autoComplete="current-password"
              />
            </Form.Item>
            <Form.Item
              name="captchaCode"
              rules={[{ required: true, message: t('login.captchaRequired') }]}
            >
              <div style={{ display: 'flex', gap: 12 }}>
                <Input
                  prefix={<SafetyOutlined style={{ color: '#bfbfbf' }} />}
                  placeholder={t('login.captcha')}
                  aria-label={t('login.captcha')}
                  autoComplete="off"
                  maxLength={6}
                  style={{ flex: 1 }}
                />
                <div
                  style={{
                    width: 120,
                    height: 40,
                    flexShrink: 0,
                    cursor: 'pointer',
                    border: '1px solid #d9d9d9',
                    borderRadius: 8,
                    overflow: 'hidden',
                    background: '#fafafa',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                  onClick={() => void refreshCaptcha()}
                  title={t('login.captchaRefresh')}
                >
                  {captchaImg ? (
                    <img
                      src={captchaImg}
                      alt={t('login.captcha')}
                      aria-label={t('login.captchaImage')}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {t('login.captchaClickToLoad')}
                    </Text>
                  )}
                </div>
              </div>
            </Form.Item>
            <Form.Item style={{ marginBottom: 16 }}>
              <Button
                type="primary"
                htmlType="submit"
                block
                loading={loading}
                style={{ height: 44, fontWeight: 500 }}
              >
                {t('login.submit')}
              </Button>
            </Form.Item>
          </Form>
          )}

          {/* 演示账号提示只在密码视图显示：二次验证那一步要的是 6 位码，此时一行
              "admin / admin123" 会把人引回上一步该填什么。 */}
          {!mfaToken && (
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
                {t('login.demoAccounts')}<Text strong style={{ color: '#595959' }}>admin</Text> /{' '}
                <Text strong style={{ color: '#595959' }}>admin123</Text>
              </Text>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
