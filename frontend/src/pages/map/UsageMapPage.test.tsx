import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import UsageMapPage from './UsageMapPage'
import { useAuthStore } from '../../store/authStore'

/** mock G6（jsdom 无 canvas 渲染环境）。hoisted 供 vi.mock 工厂引用。 */
const { renderMock, destroyMock, onMock, graphConstructor } = vi.hoisted(() => ({
  renderMock: vi.fn(),
  destroyMock: vi.fn(),
  onMock: vi.fn(),
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
    }))
  })

  it('渲染页面标题与流程 Tabs', () => {
    renderWithProviders(<UsageMapPage />)
    expect(screen.getByText(/员工使用地图/)).toBeInTheDocument()
    expect(screen.getByText(/业务主流程/)).toBeInTheDocument()
    expect(screen.getByText(/销售操作链/)).toBeInTheDocument()
    expect(screen.getByText(/客服操作链/)).toBeInTheDocument()
  })

  it('渲染高频操作快捷入口', () => {
    renderWithProviders(<UsageMapPage />)
    expect(screen.getByText('创建客户')).toBeInTheDocument()
    expect(screen.getByText('记跟进')).toBeInTheDocument()
    expect(screen.getByText('新建工单')).toBeInTheDocument()
    expect(screen.getByText('创建任务')).toBeInTheDocument()
  })

  it('G6 渲染失败时显示降级提示', async () => {
    // Graph 构造抛错 → renderFailed（延迟创建 30ms 后触发）
    graphConstructor.mockImplementation(() => {
      throw new Error('canvas not supported')
    })
    renderWithProviders(<UsageMapPage />)
    expect(await screen.findByText(/地图渲染失败/, {}, { timeout: 3000 })).toBeInTheDocument()
  })
})
