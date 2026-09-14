import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Space, Typography } from 'antd'
import { MobileOutlined } from '@ant-design/icons'

const { Text } = Typography

/** beforeinstallprompt 事件（TS 未内置该类型）。 */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

/**
 * PWA 安装提示（027-mobile-pwa，FR-005）：
 * 监听 beforeinstallprompt，触发时显示"安装到主屏幕"提示；appinstalled 后隐藏。
 */
export default function InstallPrompt() {
  const { t } = useTranslation()
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault()
      setDeferred(e as BeforeInstallPromptEvent)
    }
    const onInstalled = () => {
      setDeferred(null)
    }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  if (!deferred || dismissed) {
    return null
  }

  const onInstall = async () => {
    await deferred.prompt()
    const choice = await deferred.userChoice
    if (choice.outcome === 'accepted') {
      setDeferred(null)
    }
  }

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 16,
        left: '50%',
        transform: 'translateX(-50%)',
        zIndex: 1000,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '10px 16px',
        background: '#fff',
        borderRadius: 'var(--radius-lg)',
        boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
        border: '1px solid #e8e8e8',
        maxWidth: 'calc(100vw - 32px)',
      }}
    >
      <MobileOutlined style={{ color: '#1677ff', fontSize: 20 }} />
      <Text style={{ fontSize: 13 }}>{t('pages.installPrompt.text')}</Text>
      <Space>
        <Button size="small" type="primary" onClick={() => void onInstall()}>
          {t('pages.installPrompt.btnInstall')}
        </Button>
        <Button size="small" onClick={() => setDismissed(true)}>
          {t('pages.installPrompt.btnLater')}
        </Button>
      </Space>
    </div>
  )
}
