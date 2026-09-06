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
    expect(screen.queryByText(/pages\.installPrompt\.text/)).not.toBeInTheDocument()
  })

  it('renders after beforeinstallprompt event', () => {
    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    // 先渲染组件（挂载事件监听器），再派发事件
    dispatchPrompt()
    expect(screen.getByText(/pages\.installPrompt\.text/)).toBeInTheDocument()
  })

  it('dismiss hides prompt', () => {
    render(
      <ConfigProvider locale={zhCN}>
        <InstallPrompt />
      </ConfigProvider>,
    )
    dispatchPrompt()
    fireEvent.click(screen.getByRole('button', { name: /pages\.installPrompt\.btnLater/ }))
    expect(screen.queryByText(/pages\.installPrompt\.text/)).not.toBeInTheDocument()
  })

  it('install click calls prompt', () => {
    const promptMock = vi.fn().mockResolvedValue(undefined)
    const userChoiceMock = vi.fn().mockResolvedValue({ outcome: 'accepted' as const })
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
    fireEvent.click(screen.getByRole('button', { name: /pages\.installPrompt\.btnInstall/ }))
    expect(promptMock).toHaveBeenCalled()
  })
})
