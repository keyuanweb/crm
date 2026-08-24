import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DashboardPage from './DashboardPage'
import { fetchDashboardStats } from '../../services/statsService'
import { useAuthStore } from '../../store/authStore'
import type { DashboardStats } from '../../types/stats'

vi.mock('../../services/statsService', () => ({
  fetchDashboardStats: vi.fn(),
  saveSalesTarget: vi.fn(),
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

function buildStats(overrides: Partial<DashboardStats> = {}): DashboardStats {
  return {
    summary: {
      opportunityCount: 2,
      amountTotal: 815000,
      winRate: 0,
      customerCount: 20,
      activeCustomerCount: 18,
      newCustomersThisMonth: 5,
    },
    funnel: {
      stages: [
        { stage: 'INITIAL_CONTACT', count: 1, amountTotal: 800000, conversionRate: null },
        { stage: 'NEGOTIATING', count: 1, amountTotal: 15000, conversionRate: 1 },
      ],
      grandTotal: { stage: 'ALL', count: 2, amountTotal: 815000, conversionRate: null },
    },
    forecast: { weightedAmount: 815000, breakdown: [] },
    performance: { month: '2026-08', configured: false },
    followUps: { total: 3, byMethod: [], recent: [] },
    stalledOpportunities: [],
    generatedAt: '2026-08-23T08:00:00',
    ...overrides,
  }
}

describe('DashboardPage（006 统计仪表盘，FR-S18 首页布局与漏斗可视化）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
  })

  it('有数据时渲染统计卡与漏斗可视化进度条（阶段标签 + 金额）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('销售漏斗', {}, { timeout: 5000 })).toBeInTheDocument()
    // 漏斗可视化：阶段标签（初步接触/谈判中）+ 金额 + 占比
    expect(await screen.findByText('初步接触', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('谈判中')).toBeInTheDocument()
    expect(screen.getByText(/8,000/)).toBeInTheDocument()
    expect(screen.getByText('占比 98%')).toBeInTheDocument()
    // 统计卡
    expect(screen.getByText('商机总数')).toBeInTheDocument()
    expect(screen.getByText('金额合计')).toBeInTheDocument()
  })

  it('漏斗无数据时渲染 Empty 占位（不崩溃）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        funnel: { stages: [], grandTotal: { stage: 'ALL', count: 0, amountTotal: 0, conversionRate: null } },
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('销售漏斗', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findByText('暂无销售机会', {}, { timeout: 5000 })).toBeInTheDocument()
  })

  it('接口失败时渲染错误 Result 与重试按钮', async () => {
    vi.mocked(fetchDashboardStats).mockRejectedValue(new Error('network'))
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('统计数据加载失败', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeInTheDocument()
  })

  it('停滞商机预警以 50% 宽度卡片渲染（对称双列布局，FR-S18）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        stalledOpportunities: [
          {
            id: 1,
            opportunityName: 'CRM 采购',
            customerName: 'Acme 科技',
            amount: 300000,
            stage: 'NEGOTIATING',
            stalledDays: 12,
            lastUpdatedAt: '2026-08-11T00:00:00',
          },
        ],
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText(/停滞商机预警/, {}, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findByText('CRM 采购', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(screen.getByText('12 天')).toBeInTheDocument()
  })

  it('029：欢迎区渲染"查看使用地图"入口按钮', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())

    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText(/查看使用地图/, {}, { timeout: 5000 })).toBeInTheDocument()
  })
})
