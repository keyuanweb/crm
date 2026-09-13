import { beforeEach, describe, expect, it, vi } from 'vitest'
import { configure, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import ChannelRoiPage from './ChannelRoiPage'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`）：
// 本页虽没有 Modal/Form，但仍要把 ProCard + Table 整页渲染出来，全量并发下 1000ms 不够。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

vi.mock('../../services/marketingService', () => ({ fetchChannelRoi: vi.fn() }))

/**
 * 088 之外的独立缺陷修复 · `ChannelRoiPage` 的**金额单位**（分 → 元）。
 *
 * <p>为什么本页需要一个测试：在 2026-09-13 排查「渠道 ROI 页面加载数据有问题」之前，
 * 本页**一个测试文件都没有**（`src/pages/marketing/` 下只有另外四页的 `.perm.test.tsx`）
 * ——于是「成本 500 元显示成 `50000`」这类**静默的金额错误**没有任何东西能拦住。
 * 而金额口径恰恰是本仓库明确标注的高危项（`specs/088-frontend-layout-consistency/plan.md`
 * 把 `AmountDisplay` 的全库替换单独立项为 089，理由就是"改错是静默的金额错误"）。
 *
 * <p>数据用的是**后端真实返回过的那份**（2026-09-13 重启后的 8081 实测）：
 * `AD` → totalCost 50000 / leadCount 1 / customerCount 0 / conversionRate 0.0 / estimatedRevenue 0 / roi 0.0，
 * `EXHIBITION` → totalCost 2000 / leadCount 0 / roi null。用真实数字而不是编的，是为了让这条用例
 * 同时充当"用户看到的那一屏"的回归。
 *
 * <p>单位依据是后端的列注释，不是推测：`V32__marketing_campaign.sql:9-10` 写着
 * 「预算（分）」「成本（分）」，`MarketingRoiService:101-110` 的 totalCost 直接取该列求和、
 * estimatedRevenue 直接取商机 `expected_amount_max` 求和（同样是分）。
 * **`roi` 与 `conversionRate` 不换算**：它们是同单位相除的比值（`MarketingRoiService:111-112`），
 * 与单位无关——这条一并钉住，免得后人"顺手"把它们也除以 100。
 */
const adRow = {
  channel: 'AD',
  campaignCount: 1,
  totalCost: 50000, // 500.00 元
  leadCount: 1,
  customerCount: 0,
  conversionRate: 0,
  estimatedRevenue: 0,
  roi: 0,
}
const exhibitionRow = {
  channel: 'EXHIBITION',
  campaignCount: 1,
  totalCost: 2000, // 20.00 元
  leadCount: 0,
  customerCount: 0,
  conversionRate: null, // leadCount 为 0 时后端给 null（MarketingRoiService:111）
  estimatedRevenue: 0,
  roi: null, // totalCost 为 0 时才为 null，这里非 0 但收益为 0 → 0.0；null 留给空值行
}

async function renderPage() {
  const svc = await import('../../services/marketingService')
  vi.mocked(svc.fetchChannelRoi).mockResolvedValue([adRow, exhibitionRow] as never)

  renderWithProviders(<ChannelRoiPage />)
  // 反空洞守卫：等表格真的渲染出这两行 —— 否则后面的否定断言可能只是在"页面还没加载出来"上成立。
  await screen.findByText('enums.source.ad')
}

describe('ChannelRoiPage 的金额单位（分 → 元）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('总成本与预估收益按元显示（50000 分 → 500），不再按分原样渲染', async () => {
    await renderPage()

    // ① 两行的总成本都换算了：50000 分 → 500、2000 分 → 20。
    //    显示走的是仓库既有的 `formatAmount`（`types/opportunity.ts:72`，`toLocaleString('zh-CN')`），
    //    所以整元不带小数尾——与合同/订单/客户三个模块的金额列一致。
    expect(screen.getByText('500')).toBeInTheDocument()
    expect(screen.getByText('20')).toBeInTheDocument()

    // ② 反向钉一次原始分值得不存在 —— 只断言 ① 不够：`dataIndex` 直出时
    //    antd 会把 50000 渲染成 `50000`，**`getByText('500')` 会失败**，但若哪天
    //    有人把 render 去掉、同时改了别处，这条能立刻指认出来。
    expect(screen.queryByText('50000')).toBeNull()
    expect(screen.queryByText('2000')).toBeNull()
  })

  it('上方统计卡的金额也换算，且不出现按分渲染的合计', async () => {
    await renderPage()

    // antd 的 Statistic 把整数位与小数位拆成两个 span
    // （`node_modules/antd/es/statistic/Number.js` 的 `content-value-int` / `-decimal`），
    // 所以这里读 int 那个 span 的字面量：52000 分 → 520.00 元。
    expect(screen.getByText('520', { selector: '.ant-statistic-content-value-int' })).toBeInTheDocument()
    expect(screen.queryByText('52000')).toBeNull()
  })

  it('比值不换算：ROI 仍是百分比（0.35 → 35.0%），不是"再除以 100"', async () => {
    const svc = await import('../../services/marketingService')
    // totalCost 50000 分 / estimatedRevenue 17500 分 → roi = 0.35 → 页面显示 35.0%
    vi.mocked(svc.fetchChannelRoi).mockResolvedValue([{ ...adRow, estimatedRevenue: 17500, roi: 0.35 }] as never)
    renderWithProviders(<ChannelRoiPage />)
    await screen.findByText('enums.source.ad')

    expect(screen.getByText('35.0%')).toBeInTheDocument()
    // 预估收益同批换算成元（17500 分 → 175），且 ROI 的 35% 没有被"顺手"再除一次 100。
    // 恰好两处：表格里那一行 + 上方"预估收益"合计卡（只有一行数据，两者必然同值）。
    expect(screen.getAllByText('175')).toHaveLength(2)
  })
})
