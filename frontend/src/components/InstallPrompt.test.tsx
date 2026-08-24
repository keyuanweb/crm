import { describe, expect, it, vi, afterEach } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import InstallPrompt from './InstallPrompt'

function dispatchPrompt() {
  act(() => {
    window.dispatchEvent(new Event('beforeinstallprompt'))
  })
}

describe('InstallPrompt (027 PWA install prompt)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('no beforeinstallprompt event -> not rendered', () => {
    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    expect(screen.queryByText(/install CRM|安装 CRM/)).not.toBeInTheDocument()
  })

  it('renders after beforeinstallprompt event', () => {
    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    dispatchPrompt()
    expect(screen.getByText(/安装 CRM/)).toBeInTheDocument()
  })

  it('dismiss hides prompt', () => {
    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    dispatchPrompt()
    fireEvent.click(screen.getByRole('button', { name: /稍\s*后/ }))
    expect(screen.queryByText(/安装 CRM/)).not.toBeInTheDocument()
  })

  it('install click calls prompt', () => {
    const promptMock = vi.fn().mockResolvedValue(undefined)
    const userChoiceMock = vi.fn().mockResolvedValue({ outcome: 'accepted' })
    const evt = new Event('beforeinstallprompt')
    Object.defineProperty(evt, 'prompt', { value: promptMock })
    Object.defineProperty(evt, 'userChoice', { value: userChoiceMock })

    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    act(() => {
      window.dispatchEvent(evt)
    })
    fireEvent.click(screen.getByRole('button', { name: /安\s*装/ }))
    expect(promptMock).toHaveBeenCalled()
  })
})
