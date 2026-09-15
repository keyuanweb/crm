import { beforeEach, describe, expect, it, vi } from 'vitest'

// 093：本文件原先自带一个 `t: (key) => key` 的 react-i18next mock，它**遮蔽**了
// src/test/setup.ts 的校验版 mock —— 后者同样返回键名，但缺键时**抛错**。
// 删掉它之后，本页渲染出的每一个键都必须真实存在于 zh-CN.ts，缺键从「静默渲染出键名」
// 变成「测试红」。本页只用 useTranslation 的 t（不用 Trans / i18n.language），
// 两版 mock 的 API 差异不触及它。

// mock AnnouncementCard 避免其 useEffect 发起真实请求干扰测试
vi.mock('../../components/AnnouncementCard', () => ({
  // 本 mock 只渲染 children（`import type` 会被编译期抹除，不影响 vi.mock 的 hoist 约束）
  default: ({ children }: { children?: ReactNode }) => (
    <div data-testid="announcement-card">{children ?? 'Announcement'}</div>
  ),
}))

import { screen, within } from '@testing-library/react'
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
      winRate: 0.4,
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
    expect(screen.getByText('pages.dashboard.statCards.opportunityCount')).toBeInTheDocument()
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

  // ===== 093 仪表盘名实相符：补齐 006 三项从未渲染的验收要件 =====

  it('093：成交预测卡渲染加权总额、口径标注与分阶段分解（006 US2 / FR-D06）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        forecast: {
          weightedAmount: 815000,
          breakdown: [
            {
              stage: 'NEGOTIATING',
              amount: 400000,
              probability: 0.5,
              weighted: 200000,
              probabilitySource: 'HISTORICAL',
            },
          ],
        },
      }),
    )
    renderWithProviders(<DashboardPage />)

    const anchor = await screen.findByText('pages.dashboard.forecast.weightedTotal', {}, { timeout: 5000 })
    const card = anchor.closest('.ant-card') as HTMLElement
    expect(card).not.toBeNull()
    // 主数字走 formatAmount（分转元）：815000 分 = 8,150 元
    expect(within(card).getByText('8,150')).toBeInTheDocument()
    expect(within(card).getByText('pages.dashboard.forecast.weightedTotal')).toBeInTheDocument()
    // 口径标注是本卡最重要的一行：不写它，读卡人会拿它跟旁边的「业绩达成」相除
    expect(within(card).getByText('pages.dashboard.forecast.scopeNote')).toBeInTheDocument()
    // 分阶段分解：金额分转元、概率按百分比、来源徽标走 probabilitySource 的三值分支
    expect(within(card).getByText('4,000')).toBeInTheDocument()
    expect(within(card).getByText('50%')).toBeInTheDocument()
    expect(within(card).getByText('pages.dashboard.forecast.historicalCalibration')).toBeInTheDocument()
  })

  it('093：客户分析卡渲染三项客户指标，且不额外请求（006 FR-D07）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())
    renderWithProviders(<DashboardPage />)

    // 锚在卡体文案上而不是卡头标题：Card 的 loading 态会先渲染卡头、卡体还是骨架
    const anchor = await screen.findByText('pages.dashboard.customerAnalysis.totalCustomers', {}, { timeout: 5000 })
    const card = anchor.closest('.ant-card') as HTMLElement
    expect(within(card).getByText('pages.dashboard.customerAnalysis.activeCustomers')).toBeInTheDocument()
    expect(within(card).getByText('pages.dashboard.customerAnalysis.newThisMonth')).toBeInTheDocument()
    // 三个数全部取自已加载的 summary：20 / 18 / 5
    expect(within(card).getByText('20')).toBeInTheDocument()
    expect(within(card).getByText('18')).toBeInTheDocument()
    expect(within(card).getByText('5')).toBeInTheDocument()
    // 客户分析不发第二个请求
    expect(vi.mocked(fetchDashboardStats)).toHaveBeenCalledTimes(1)
  })

  it('093：跟进活动卡与最近跟进表渲染真实 followUps（006 FR-D08）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        followUps: {
          total: 3,
          byMethod: [
            { method: 'PHONE', count: 2 },
            { method: 'EMAIL', count: 1 },
            // 登记表里没有的码：必须原样显示，不得并进「其他」——两种不同的方式
            // 渲染成同一种，比显示一个没人见过的编码更难排查
            { method: 'WECHAT', count: 4 },
          ],
          recent: [
            {
              id: 9,
              method: 'PHONE',
              content: '电话沟通续约',
              customerName: 'Acme 科技',
              followUpBy: '王销售',
              createdAt: '2026-08-22T10:30:00',
            },
          ],
        },
      }),
    )
    renderWithProviders(<DashboardPage />)

    const activityAnchor = await screen.findByText('pages.dashboard.followUpActivity.byMethod', {}, { timeout: 5000 })
    const activityCard = activityAnchor.closest('.ant-card') as HTMLElement
    expect(within(activityCard).getByText('pages.dashboard.followUpActivity.totalFollowUps')).toBeInTheDocument()
    // 方式码服务端是大写（PHONE/EMAIL），走 ENUM_KEYS.followUpMethod 登记表
    expect(within(activityCard).getByText('enums.followUpMethod.phone')).toBeInTheDocument()
    expect(within(activityCard).getByText('enums.followUpMethod.email')).toBeInTheDocument()
    // 未知码回退原值（不是 other）
    expect(within(activityCard).getByText('WECHAT')).toBeInTheDocument()
    expect(within(activityCard).queryByText('enums.other')).toBeNull()

    const recentAnchor = await screen.findByText('pages.dashboard.recentFollowUp.method', {}, { timeout: 5000 })
    const recentCard = recentAnchor.closest('.ant-card') as HTMLElement
    expect(within(recentCard).getByText('电话沟通续约')).toBeInTheDocument()
    expect(within(recentCard).getByText('Acme 科技')).toBeInTheDocument()
    expect(within(recentCard).getByText('王销售')).toBeInTheDocument()
    // 时间按本页既有格式（replace('T',' ').slice(0,19)）
    expect(within(recentCard).getByText('2026-08-22 10:30:00')).toBeInTheDocument()
  })

  it('093：KPI 行按 FR-D02 补上赢单率，winRate 是 0..1 小数不是百分数', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('pages.dashboard.statCards.winRate', {}, { timeout: 5000 })).toBeInTheDocument()
    // 0.4 必须渲染成 40.0%，而不是 0.4%
    expect(screen.getByText('40.0%')).toBeInTheDocument()
  })

  it('093：赢单率为 0 时渲染 0.0% 而不是 NaN（后端 closed==0 时给 0）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        summary: {
          opportunityCount: 0,
          amountTotal: 0,
          winRate: 0,
          customerCount: 0,
          activeCustomerCount: 0,
          newCustomersThisMonth: 0,
        },
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('pages.dashboard.statCards.winRate', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('0.0%')).toBeInTheDocument()
  })

  // 负向断言是本批次的真凭据：只写「应该没有」而没验过它会红，等于没写（SC-001 / SC-005）
  it('093：伪造的待办/活动卡与「较上月」假同比均已消失', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(buildStats())
    renderWithProviders(<DashboardPage />)

    await screen.findByText('pages.dashboard.funnel.title', {}, { timeout: 5000 })
    // 假同比：t 返回 key，这串 key 只可能来自被删掉的 KpiCard trend prop
    expect(screen.queryByText('home.trendVsLastMonth')).toBeNull()
    // 两块凭空捏造的卡片
    expect(screen.queryByText('pages.dashboard.todo.title')).toBeNull()
    expect(screen.queryByText('pages.dashboard.activity.title')).toBeNull()
    // FR-D02 之外的两个旧 KPI 标签
    expect(screen.queryByText('pages.dashboard.statCards.totalCustomers')).toBeNull()
    expect(screen.queryByText('pages.dashboard.statCards.activeOpportunities')).toBeNull()
  })

  it('093：三个新卡位各自有空态，不只在有数据时才成立（FR-015）', async () => {
    vi.mocked(fetchDashboardStats).mockResolvedValue(
      buildStats({
        forecast: { weightedAmount: 0, breakdown: [] },
        followUps: { total: 3, byMethod: [], recent: [] },
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('pages.dashboard.forecast.empty', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('pages.dashboard.followUpActivity.empty')).toBeInTheDocument()
    expect(screen.getByText('pages.dashboard.recentFollowUp.empty')).toBeInTheDocument()
    // 跟进总数为 3 时仍然渲染总数，只是分布与明细为空 —— 空态是按区块判的，不是整页
    expect(screen.getByText('pages.dashboard.followUpActivity.totalFollowUps')).toBeInTheDocument()
  })
})
