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

/** 只声明本用例真要摸的那几个字段（G6 被整体 mock，构造参数就是页面自己的对象字面量）。 */
interface GraphProbe {
  data: { nodes: { id: string; data: { warning?: boolean; color: string } }[] }
  node: {
    style: {
      stroke: (d: { data: { color: string; warning?: boolean } }) => string
      lineWidth: (d: { data: { warning?: boolean } }) => number
    }
  }
}

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
    expect(screen.getByText(/pages\.usageMap\.pageTitle/)).toBeInTheDocument()
    expect(screen.getAllByText(/pages\.usageMap\.tabProcess/).length).toBeGreaterThan(0)
    expect(screen.getByText(/pages\.usageMap\.tabState/)).toBeInTheDocument()
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
    expect(await screen.findByText(/pages\.usageMap\.renderFailed/, {}, { timeout: 3000 })).toBeInTheDocument()
  })

  it('节点详情弹窗打开/关闭', async () => {
    renderWithProviders(<UsageMapPage />)
    expect(await screen.findByText(/pages\.usageMap\.pageTitle/)).toBeInTheDocument()

    // 节点 id 取自 ADMIN 流程：登录用户是 ADMIN，默认展示的是 admin 流程而非主流程（m1 不在其中）
    await waitFor(() => expect(onMock).toHaveBeenCalledWith('node:click', expect.any(Function)))
    const mockClickHandler = onMock.mock.calls.find((call) => call[0] === 'node:click')?.[1]
    expect(mockClickHandler).toBeDefined()

    mockClickHandler?.({ target: { id: 'a1' } })

    // 验证弹窗打开（状态更新须经一次重渲染）
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
    expect(screen.getByText('系统配置')).toBeInTheDocument()

    // 验证关闭按钮（已改为 i18n 文案：测试环境 react-i18next 被 mock 成 t(key) => key，故断言键名）
    const closeBtn = screen.getByRole('button', { name: /common\.button\.close/ })
    fireEvent.click(closeBtn)

    // 验证弹窗关闭
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('状态流转视图切换', () => {
    renderWithProviders(<UsageMapPage />)
    
    // 切换到状态流转视图
    const stateTab = screen.getByText('pages.usageMap.tabState')
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

  /**
   * 067 T009 · 状态流转视图的「异常状态红色警示」。
   *
   * ## 这条用例证到哪、没证到哪（jsdom 无 canvas，G6 被整体 mock）
   *
   * - **证到**：喂给 G6 的样式是个**函数**，且该函数把 `warning` 映射成「红边框 + 粗边框」；
   *   并且**该视图的节点数据里真的有** `warning: true` 的节点 —— 少了后半条，
   *   前一条映射永远不触发，警示等于不存在（本仓有过这种「配置对但没人吃」的先例）。
   * - **没证到**：像素上真的画出了红边框。那需要真实 canvas/G6 渲染，本仓单测没有 ⇒
   *   这是**配置与数据层**的护栏，**不是**视觉回归；视觉部分见 `quickstart.md` 的手工冒烟表。
   */
  it('状态流转的异常节点带红色警示（067 T009）', async () => {
    renderWithProviders(<UsageMapPage />)
    const before = graphConstructor.mock.calls.length

    fireEvent.click(screen.getByText('pages.usageMap.tabState'))

    // 切 Tab 会**重建** Graph（30ms 延迟）⇒ 等的是「又构造了一次」，
    // **不能**用 `toHaveBeenCalled()` —— 首屏那次就已经为真，等不到新图（本仓有过这种空等）。
    await waitFor(() => expect(graphConstructor.mock.calls.length).toBeGreaterThan(before))
    // 取**最后一次**构造的参数（本仓的 lib 不含 ES2022，故写索引而不用 `.at(-1)`）
    const calls = graphConstructor.mock.calls
    const opts = calls[calls.length - 1]?.[0] as unknown as GraphProbe

    // ① 数据层：默认状态流是「合同状态流转」，其中两个节点带 warning。
    //    cs7 自带的 color 是灰的（#8c8c8c）⇒ 红**只能**来自 warning 分支，蒙不对。
    expect(opts.data.nodes.filter((n) => n.data.warning).map((n) => n.id)).toEqual(['cs6', 'cs7'])

    // ② 配置层：warning ⇒ 红边框 + 粗边框
    expect(opts.node.style.stroke({ data: { warning: true, color: '#8c8c8c' } })).toBe('#cf1322')
    expect(opts.node.style.lineWidth({ data: { warning: true } })).toBe(3)

    // ③ 对照（免得把「一律变红」当成通过）：非 warning 用自己的颜色，缺色回落到品牌蓝
    expect(opts.node.style.stroke({ data: { warning: false, color: '#8c8c8c' } })).toBe('#8c8c8c')
    expect(opts.node.style.stroke({ data: { warning: false, color: undefined as unknown as string } })).toBe('#1677ff')
    expect(opts.node.style.lineWidth({ data: { warning: false } })).toBe(2)

    // ④ 悬停移出必须按 warning 复原：原实现一律恢复成 lineWidth 2，
    //    会把异常节点的粗边框**在第一次移出后**抹掉（源码该处已有「不能一律恢复成 2」的注释）。
    //    取**最后一次**注册的 handler —— 首屏那次是流程视图的闭包，拿错了就不是本视图。
    const leaveCalls = onMock.mock.calls.filter((c) => c[0] === 'node:mouseleave')
    const leave = leaveCalls[leaveCalls.length - 1]?.[1]
    leave?.({ target: { id: 'cs6' } })
    expect(updateNodeDataMock).toHaveBeenLastCalledWith([
      { id: 'cs6', style: { shadow: '0 2px 8px rgba(0,0,0,0.15)', lineWidth: 3 } },
    ])
  })
})
