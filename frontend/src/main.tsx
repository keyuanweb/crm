import React, { useMemo } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import { ProConfigProvider, zhCNIntl, enUSIntl } from '@ant-design/pro-components'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'
import ErrorBoundary from './components/ErrorBoundary'
import App from './App'
import './i18n'
import './index.css'
import { useTranslation } from 'react-i18next'

// 027：生产环境注册 Service Worker（PWA 离线应用外壳）
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // SW 注册失败不影响应用使用
    })
  })
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function LocaleProvider({ children }: { children: React.ReactNode }) {
  const { i18n } = useTranslation()
  const locale = useMemo(() => {
    const lang = i18n.language || 'zh-CN'
    if (lang.startsWith('en')) return enUS
    return zhCN
  }, [i18n.language])

  const proLocale = useMemo(() => {
    const lang = i18n.language || 'zh-CN'
    if (lang.startsWith('en')) return enUSIntl
    return zhCNIntl
  }, [i18n.language])

  React.useEffect(() => {
    const lang = i18n.language || 'zh-CN'
    if (lang.startsWith('en')) {
      dayjs.locale('en')
    } else {
      dayjs.locale('zh-cn')
    }
  }, [i18n.language])

  return (
    <ConfigProvider locale={locale}>
      <ProConfigProvider intl={proLocale}>
        {children}
      </ProConfigProvider>
    </ConfigProvider>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <LocaleProvider>
      <ErrorBoundary>
        <AntApp>
          <BrowserRouter>
            <QueryClientProvider client={queryClient}>
              <App />
            </QueryClientProvider>
          </BrowserRouter>
        </AntApp>
      </ErrorBoundary>
    </LocaleProvider>
  </React.StrictMode>,
)
