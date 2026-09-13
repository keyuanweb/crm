import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import PageState from './PageState'

/**
 * 页面三态原语（088 交付物 2）。
 *
 * <p>测试环境里 `t(key)` 原样返回键名，但**缺键会抛错**（`src/test/setup.ts:107-113`，
 * 只校验 zh-CN）。所以下面断言出的 `common.state.loading` 这类字符串是
 * **"该键真的存在于 zh-CN"**的证据，而不是"恰好渲染了键名"。
 * 这三个键是 088 新增的，与 `common.state.*` 的 `en.ts` 同名键由 `pnpm i18n:check` 双向比对。
 *
 * <p>三条分支都要走到：`PageState` 是一个组件含三条路径（合并成一个的理由见源文件头），
 * 若只测其中一条，另两条就是纯负债。
 */
describe('PageState', () => {
  it('loading：渲染 Spin 与加载文案（走 antd 嵌套形态，裸 tip 不显示文案）', () => {
    renderWithProviders(<PageState state="loading" />)

    const box = screen.getByTestId('page-state-loading')
    expect(box).toBeInTheDocument()
    // 反假绿：`tip` 在非嵌套形态下 antd 不渲染文案，这条断言正是钉住"用了嵌套形态"。
    expect(screen.getByText('common.state.loading')).toBeInTheDocument()
  })

  it('empty：默认复用既有的 common.message.no_data，不新增键', () => {
    renderWithProviders(<PageState state="empty" />)

    expect(screen.getByTestId('page-state-empty')).toBeInTheDocument()
    expect(screen.getByText('common.message.no_data')).toBeInTheDocument()
  })

  it('empty：文案可覆盖（调用方要区分"没有客户"与"没有筛选结果"）', () => {
    renderWithProviders(<PageState state="empty" emptyText="没有匹配的客户" />)
    expect(screen.getByText('没有匹配的客户')).toBeInTheDocument()
    expect(screen.queryByText('common.message.no_data')).toBeNull()
  })

  it('error：默认展示错误文案，且**没有** onRetry 时不渲染重试按钮', () => {
    renderWithProviders(<PageState state="error" />)

    expect(screen.getByTestId('page-state-error')).toBeInTheDocument()
    expect(screen.getByText('common.state.error')).toBeInTheDocument()
    // 这一条是结构性的：错误态里"重试按钮存在"必须由 onRetry 决定，
    // 而不是每个调用点自己记得放一个按钮。
    expect(screen.queryByRole('button', { name: 'common.state.retry' })).toBeNull()
  })

  it('error：传了 onRetry 才有重试按钮，点击真的会调到它', () => {
    const onRetry = vi.fn()
    renderWithProviders(<PageState state="error" onRetry={onRetry} />)

    const retry = screen.getByRole('button', { name: 'common.state.retry' })
    fireEvent.click(retry)
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('error：文案可覆盖（把服务端返回的具体原因显示出来）', () => {
    renderWithProviders(<PageState state="error" errorText="网络超时" />)
    expect(screen.getByText('网络超时')).toBeInTheDocument()
  })

  it('三态互斥：loading 时不渲染空态与错误态', () => {
    renderWithProviders(<PageState state="loading" onRetry={() => {}} />)

    expect(screen.queryByTestId('page-state-empty')).toBeNull()
    expect(screen.queryByTestId('page-state-error')).toBeNull()
  })
})
