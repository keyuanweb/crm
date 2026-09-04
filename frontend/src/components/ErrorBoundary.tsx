import { useEffect, useState } from 'react'
import { Component, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Result } from 'antd'
import i18n from '../i18n'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/** 全局错误边界：任一页面渲染异常时展示友好错误页，避免整页白屏。 */
class ErrorBoundaryInner extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return <ErrorBoundaryContent />
    }
    return this.props.children
  }
}

function ErrorBoundaryContent() {
  const { t } = useTranslation()
  const [version, setVersion] = useState(0)

  // 监听语言变化，语言切换时强制重新渲染
  useEffect(() => {
    i18n.on('languageChanged', () => setVersion((v) => v + 1))
    return () => {
      // cleanup
    }
  }, [])

  return (
    <Result key={version} status="error" title={t('pages.errorBoundary.title')} subTitle={t('pages.errorBoundary.subTitle')} extra={<Button type="primary" onClick={() => window.location.reload()}>{t('pages.errorBoundary.btnRefresh')}</Button>} />
  )
}

export default ErrorBoundaryInner
