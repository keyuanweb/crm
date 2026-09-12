import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import UsageMapPage from './UsageMapPage'
import { useAuthStore } from '../../store/authStore'

/** mock G6（jsdom 无 canvas 渲染环境）。hoisted 供 vi.mock 工厂引用。 */
const { renderMock, destroyMock, onMock, updateNodeDataMock, graphConstructor } = vi.hoisted(() => ({
  renderMock: vi.fn(),
  destroyMock: vi.fn(),
  // 显式声明参数元组，避免 any：不声明时 mock.calls 的元素是 any[]
  // （vitest 1.6 的签名是 fn<TArgs extends any[], R>()，泛型是「参数元组」而不是函数类型）
  onMock: vi.fn<[string, (evt: unknown) => void]>(),
  updateNodeDataMock: vi.fn(),
  graphConstructor: vi.fn(),
}))

vi.mock('@antv/g6', () => ({
  Graph: graphConstructor,
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

describe('UsageMapPage（029 员工使用地图渲染）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
    renderMock.mockResolvedValue(undefined)
    graphConstructor.mockImplementation(() => ({
      render: renderMock,
      destroy: destroyMock,
      on: onMock,
      hasNode: () => true,
      updateNodeData: updateNodeDataMock,
    }))
    // 全局 matchMedia 打桩为 matches:false → Grid.useBreakpoint().lg 为假 → isMobile 为真 → 详情 Modal 恒不打开。
    // 本用例需要桌面布局才能断言弹窗，故仅在本文件内覆盖为「min-width 查询命中」（其它文件仍用全局桩）。
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: query.includes('min-width'),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    })
  })

  it('渲染页面标题与流程 Tabs', () => {
    renderWithProviders(<UsageMapPage />)
    expect(screen.getByText(/员工使用地图/)).toBeInTheDocument()
    // 使用更具体的选择器避免匹配到描述文本
    expect(screen.getAllByText(/业务流程/).length).toBeGreaterThan(0)
    expect(screen.getByText(/状态流转/)).toBeInTheDocument()
  })

  it('渲染高频操作快捷入口', () => {
    renderWithProviders(<UsageMapPage />)
    expect(screen.getByText('新建线索')).toBeInTheDocument()
    expect(screen.getByText('新建客户')).toBeInTheDocument()
    expect(screen.getByText('新建工单')).toBeInTheDocument()
    expect(screen.getByText('工作台')).toBeInTheDocument()
  })

  it('G6 渲染失败时显示降级提示', async () => {
    // Graph 构造抛错 → renderFailed（延迟创建 30ms 后触发）
    graphConstructor.mockImplementation(() => {
      throw new Error('canvas not supported')
    })
    renderWithProviders(<UsageMapPage />)
    expect(await screen.findByText(/地图渲染失败/, {}, { timeout: 3000 })).toBeInTheDocument()
  })

  it('节点详情弹窗打开/关闭', async () => {
    renderWithProviders(<UsageMapPage />)
    expect(await screen.findByText(/员工使用地图/)).toBeInTheDocument()

    // 节点 id 取自 ADMIN 流程：登录用户是 ADMIN，默认展示的是 admin 流程而非主流程（m1 不在其中）
    await waitFor(() => expect(onMock).toHaveBeenCalledWith('node:click', expect.any(Function)))
    const mockClickHandler = onMock.mock.calls.find((call) => call[0] === 'node:click')?.[1]
    expect(mockClickHandler).toBeDefined()

    mockClickHandler?.({ target: { id: 'a1' } })

    // 验证弹窗打开（状态更新须经一次重渲染）
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
    expect(screen.getByText('系统配置')).toBeInTheDocument()

    // 验证关闭按钮（antd 会在两个汉字之间自动插空格，故按角色 + 正则匹配）
    const closeBtn = screen.getByRole('button', { name: /关\s*闭/ })
    fireEvent.click(closeBtn)

    // 验证弹窗关闭
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('状态流转视图切换', () => {
    renderWithProviders(<UsageMapPage />)
    
    // 切换到状态流转视图
    const stateTab = screen.getByText('状态流转')
    fireEvent.click(stateTab)
    
    // 验证状态流转标题存在
    expect(screen.getByText(/合同状态流转/)).toBeInTheDocument()
  })

  it('快捷入口按钮存在且可点击', () => {
    renderWithProviders(<UsageMapPage />)
    
    // 找到快捷入口按钮
    const createCustomerBtn = screen.queryByText(/新建客户/)
    
    expect(createCustomerBtn).toBeInTheDocument()
    
    // 验证按钮可点击（不验证具体悬停样式，因为 Ant Design Button 组件结构复杂）
    if (createCustomerBtn) {
      expect(() => fireEvent.click(createCustomerBtn)).not.toThrow()
    }
  })

  it('节点悬停效果', async () => {
    renderWithProviders(<UsageMapPage />)

    // 图实例在 30ms 延迟后创建（StrictMode 双挂载防护），必须等注册完成再取 handler。
    // 原实现未等待：两个 handler 都是 undefined，整段断言被 if 跳过——用例恒绿但什么都没验证。
    await waitFor(() => expect(onMock).toHaveBeenCalledWith('node:mouseenter', expect.any(Function)))
    const mouseEnterHandler = onMock.mock.calls.find((call) => call[0] === 'node:mouseenter')?.[1]
    const mouseLeaveHandler = onMock.mock.calls.find((call) => call[0] === 'node:mouseleave')?.[1]

    // 悬停：阴影加深 + 边框加粗（节点 id 取自 ADMIN 流程，理由同上一用例）
    mouseEnterHandler?.({ target: { id: 'a1' } })
    expect(updateNodeDataMock).toHaveBeenCalledWith([
      { id: 'a1', style: { shadow: '0 4px 12px rgba(0,0,0,0.25)', lineWidth: 3 } },
    ])

    // 移出：恢复初始样式（a1 非异常节点，边框回到 2）
    mouseLeaveHandler?.({ target: { id: 'a1' } })
    expect(updateNodeDataMock).toHaveBeenLastCalledWith([
      { id: 'a1', style: { shadow: '0 2px 8px rgba(0,0,0,0.15)', lineWidth: 2 } },
    ])
  })
})
