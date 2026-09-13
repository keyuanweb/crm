import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { MemoryRouter } from 'react-router-dom'
import type { ReactElement } from 'react'
import { antdTheme } from '../theme'

/**
 * 与 main.tsx 对齐的测试渲染助手：ConfigProvider(zhCN + 主题) + antd App（message 上下文）
 * + React Query + Router。
 *
 * `theme` 与生产同源（088）：测试若不带主题，就会出现"测试里绿、线上另一套配色"的偏差，
 * 而 088 的整个前提就是"主题是单一真源"。带上它，`FormGrid` 的栅格下限这类依赖
 * 主题 token 的行为才在测试里也是真的。
 */
export function renderWithProviders(ui: ReactElement, options: { route?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ConfigProvider locale={zhCN} theme={antdTheme}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[options.route ?? '/']}>{ui}</MemoryRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  )
}
