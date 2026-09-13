import { beforeEach, describe, expect, it, vi } from 'vitest'
import { configure, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import CampaignListPage from './CampaignListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`）：
// 本页要挂载 antd Modal + Form，是全仓最重的形态。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(),
  createCampaign: vi.fn(),
  updateCampaign: vi.fn(),
  startCampaign: vi.fn(),
  endCampaign: vi.fn(),
  deleteCampaign: vi.fn(),
  fetchChannelRoi: vi.fn(),
}))

/**
 * 088 之外的独立缺陷修复 · `CampaignListPage` 的**金额单位**（分 ↔ 元，双向）。
 *
 * <p>为什么必须新开一个文件：`CampaignListPage.perm.test.tsx` 管的是 086 的权限语义
 * （谁看得见「新增/开始/结束/编辑/删除」），它的数据里虽然有 `budget: 1000`，
 * 却**从不断言这个值显示成什么**、也从不打开弹窗——于是本页的金额口径在它眼里是零执行的。
 *
 * <p>本页的缺陷比 ROI 页更重，因为它是**双向**的：后端 `marketing_campaign.budget`/`cost`
 * 是分（`V32__marketing_campaign.sql:9-10` 的列注释「预算（分）」「成本（分）」），改前
 * ① 表格按分原样渲染（500 元显示成 `50000`）；
 * ② 编辑时把 `50000` 填进一个没有单位的输入框，保存时又原样回传。
 * ②的后果不是"显示难看"：用户**只要动过金额字段**，改的就是 100 倍——而这条路径上没有任何报错。
 * 所以「回填」与「提交」两半各有一条用例，缺一条都可能只修了半个往返。
 *
 * <p>用 ADMIN：本文件不涉及权限语义，而 `hasPerm` 对 ADMIN 直通，
 * 于是行内「编辑」一定在，权限不会变成干扰变量。
 *
 * <p>刻意**不**覆盖"新建"分支：它走的是同一个 `onSave`（元→分只写了一次），
 * 而要在 `Create` 弹窗里把「渠道」这个 antd Select 选中，会引入与本缺陷无关的交互噪声。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 预算 500 元、成本 123.45 元的活动；状态给 PLANNING，让行内「编辑」（非 ENDED 才在）出现。 */
const campaignRow = {
  id: 1,
  name: '筹备活动',
  channel: 'WEBSITE',
  budget: 50000, // 500 元
  cost: 12345, // 123.45 元——刻意取一个带小数的值，好把"整元"与"有角分"两条显示路径都走到
  status: 'PLANNING',
  leadCount: 0,
  customerCount: 0,
  version: 0,
}

const BTN_EDIT = 'pages.marketing.campaign.btnEdit'
const BTN_SAVE = 'pages.marketing.campaign.btnSave'
const FORM_BUDGET = /pages\.marketing\.campaign\.formBudget/

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const svc = await import('../../services/marketingService')
  vi.mocked(svc.fetchCampaigns).mockResolvedValue({ items: [campaignRow], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(svc.updateCampaign).mockResolvedValue({} as never)

  renderWithProviders(<CampaignListPage />)
  // 反空洞守卫：等表格真的渲染出这一行 —— 否则后面的否定断言可能是在"列表还没回来"上成立的。
  await screen.findByText(campaignRow.name)
}

/** 点行内「编辑」并返回弹窗。 */
async function openEdit() {
  fireEvent.click(screen.getByText(BTN_EDIT))
  return await screen.findByRole('dialog')
}

describe('CampaignListPage 的金额单位（分 ↔ 元）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('表格按元显示（50000 分 → 500、12345 分 → 123.45），不再按分原样渲染', async () => {
    await renderPage()

    // 显示走仓库既有的 `formatAmount`（`types/opportunity.ts:72`，`toLocaleString('zh-CN')`）：
    // 整元不带小数尾、有角分才带——与合同/订单/客户三个模块一致。
    expect(screen.getByText('500')).toBeInTheDocument()
    expect(screen.getByText('123.45')).toBeInTheDocument()
    // 反向钉一次原始分值得的不存在：只断言上面两条的话，"分照原样显示"时
    // `getByText('500')` 会失败，但失败原因不够直白。
    expect(screen.queryByText('50000')).toBeNull()
    expect(screen.queryByText('12345')).toBeNull()
  })

  it('编辑回填按元（50000 分 → 输入框里是 500），不是把分值灌进"元"的框里', async () => {
    await renderPage()
    const dialog = await openEdit()

    expect(await within(dialog).findByDisplayValue('500')).toBeInTheDocument()
    expect(within(dialog).getByDisplayValue('123.45')).toBeInTheDocument()
    // 这一条是本页缺陷的原始形态：改前输入框里是 `50000`，用户一按保存就把金额改成 100 倍。
    expect(within(dialog).queryByDisplayValue('50000')).toBeNull()
  })

  it('提交按分（输入 199.99 元 → payload 19999 分），且带上乐观锁的 version', async () => {
    await renderPage()
    const { updateCampaign } = await import('../../services/marketingService')
    const dialog = await openEdit()
    await within(dialog).findByDisplayValue('500')

    fireEvent.change(within(dialog).getByLabelText(FORM_BUDGET), { target: { value: '199.99' } })
    fireEvent.click(within(dialog).getByRole('button', { name: new RegExp(BTN_SAVE) }))

    await waitFor(() => expect(updateCampaign).toHaveBeenCalledTimes(1))
    const [id, payload] = vi.mocked(updateCampaign).mock.calls[0]
    expect(id).toBe(1)
    // `199.99 * 100` 在浮点下是 19998.999…，所以页面里必须 `Math.round` —— 少了它就是 19998 分的静默误差。
    expect(payload.budget).toBe(19999)
    // 未改动的成本也要按分回传，且要**逐字回到原值**（123.45 元 → 12345 分）：
    // 回填与提交两半若不对称，这里就会变成 12344 或 1234500——那是用户没碰过的字段被改掉。
    expect(payload.cost).toBe(12345)
    // version 必须带上：后端靠它做乐观锁，漏了就是"静默覆盖他人的修改"。
    expect(payload.version).toBe(0)
  })
})
