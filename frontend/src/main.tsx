import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import ErrorBoundary from './components/ErrorBoundary'
import App from './App'
import './index.css'

// 027：生产环境注册 Service Worker（PWA 离线应用外壳）
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // SW 注册失败不影响应用使用
    })
  })
}

dayjs.locale('zh-cn')

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <ConfigProvider locale={zhCN}>
        <AntApp>
          <BrowserRouter>
            <QueryClientProvider client={queryClient}>
              <App />
            </QueryClientProvider>
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </ErrorBoundary>
  </React.StrictMode>,
)
