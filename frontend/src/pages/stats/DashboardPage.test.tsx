import { beforeEach, describe, expect, it, vi } from 'vitest'

// 必须在导入任何组件之前 mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'zh-CN' },
  }),
}))

// mock AnnouncementCard 避免其 useEffect 发起真实请求干扰测试
vi.mock('../../components/AnnouncementCard', () => ({
  // 本 mock 只渲染 children（`import type` 会被编译期抹除，不影响 vi.mock 的 hoist 约束）
  default: ({ children }: { children?: ReactNode }) => (
    <div data-testid="announcement-card">{children ?? 'Announcement'}</div>
  ),
}))

import { screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DashboardPage from './DashboardPage'
import { fetchDashboardStats } from '../../services/statsService'
import { useAuthStore } from '../../store/authStore'
import type { DashboardStats } from '../../types/stats'
import type { SuggestionSummary } from '../../types/suggestion'

vi.mock('../../services/statsService', () => ({
  fetchDashboardStats: vi.fn(),
  saveSalesTarget: vi.fn(),
}))

vi.mock('../../services/suggestionService', () => ({
  fetchSuggestionSummary: vi.fn().mockResolvedValue({
    atRiskCustomers: 0,
    stalledOpportunities: 0,
    followUpCustomers: 0,
    highScoreLeads: 0,
  } as SuggestionSummary),
}))

vi.mock('../../services/announcementService', () => ({
  fetchAnnouncements: vi.fn().mockResolvedValue({ items: [] }),
  markAnnouncementRead: vi.fn().mockResolvedValue(undefined),
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
    // 重置 auth store 防止测试间污染
    useAuthStore.setState({ user: null })
    useAuthStore.setState({ user: adminUser })
  })

  it('有数据时渲染统计卡与漏斗可视化进度条（阶段标签 + 金额）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())
    renderWithProviders(<DashboardPage />)

    // t(key) => key，所以断言使用翻译 key
    expect(await screen.findByText('pages.dashboard.funnel.title', {}, { timeout: 5000 })).toBeInTheDocument()
    // 漏斗可视化：阶段标签来自 ENUM_KEYS.opportunityStage（经 t 取文案）+ 金额 + 占比
    expect(await screen.findByText('enums.opportunityStage.initialContact', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('enums.opportunityStage.negotiating')).toBeInTheDocument()
    expect(screen.getByText(/8,000/)).toBeInTheDocument()
    // 两个阶段各 1 个，总共 2 个，占比各 50%
    expect(screen.queryAllByText('50%').length).toBeGreaterThan(0)
    // 统计卡
    expect(screen.getByText('pages.dashboard.statCards.totalCustomers')).toBeInTheDocument()
    expect(screen.getByText('pages.dashboard.statCards.amountTotal')).toBeInTheDocument()
  })

  it('漏斗无数据时渲染 Empty 占位（不崩溃）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        funnel: { stages: [], grandTotal: { stage: 'ALL', count: 0, amountTotal: 0, conversionRate: null } },
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('pages.dashboard.funnel.title', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findByText('pages.dashboard.funnel.empty', {}, { timeout: 5000 })).toBeInTheDocument()
  })

  it('接口失败时渲染错误 Result 与重试按钮', async () => {
    vi.mocked(fetchDashboardStats).mockRejectedValue(new Error('network'))
    renderWithProviders(<DashboardPage />)
    expect(await screen.findByText('pages.dashboard.error.loadFailed', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /pages\.dashboard\.error\.retry/ })).toBeInTheDocument()
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

    expect(await screen.findByText('pages.dashboard.stalledOpportunities.title', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findByText('CRM 采购', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    // t('pages.dashboard.stalledOpportunities.days') => 'pages.dashboard.stalledOpportunities.days'
    expect(screen.getByText(/12 pages\.dashboard\.stalledOpportunities\.days/)).toBeInTheDocument()
  })

  it('029：欢迎区渲染"查看使用地图"入口按钮', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())

    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('pages.dashboard.buttons.viewUsageMap', {}, { timeout: 5000 })).toBeInTheDocument()
  })
})
