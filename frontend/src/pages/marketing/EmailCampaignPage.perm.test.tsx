import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import EmailCampaignPage from './EmailCampaignPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/emailService', () => ({
  fetchEmailTemplates: vi.fn(),
  fetchEmailCampaigns: vi.fn(),
  fetchCampaignDetail: vi.fn(),
  createEmailCampaign: vi.fn(),
  testSendCampaign: vi.fn(),
}))
// 页面挂载时的 useEffect 会拉细分下拉；不替身化就会真去建 axios 客户端。
vi.mock('../../services/segmentService', () => ({ fetchSegments: vi.fn() }))

/**
 * 087 权限用例回填 · `EmailCampaignPage` 行内「测试」的双向渲染测试。
 *
 * <p><b>控件 → 判据 → 码</b>：动作列里**第二个**链接「测试」（`key="test"`，即测试发送）由
 * `can[PERMS.emailManage]` 决定是否渲染（`EmailCampaignPage.tsx:183`），
 * `PERMS.emailManage === 'email:manage'`（`constants/permissions.ts:177`）。后端对得上：
 * `POST /email-campaigns/{id}/test` 挂的正是 `email:manage`（`EmailController` 全线只有一个写码）；
 * service 侧 `testSendCampaign` 就是这条路径（`services/emailService.ts:40-43`）。
 *
 * <p><b>与 `EmailUnsubscribePage` 是同一个码、两个独立站点</b>：两页共用 `email:manage`，
 * 但判据分别落在各自的 `tsx` 行上，改一处不会牵动另一处。所以本文件独立成对（正/负各一），
 * 不复用也不推定退订页的结论——"一个站点被覆盖"不能推出"另一个站点被覆盖"。
 *
 * <p><b>动作列里「记录」刻意未收口</b>（086 只收破坏性/高权动作，查记录不是），
 * 因此它每例都必须在场：它既是"页面确实渲染过"的反空洞守卫，也防止有人收口时手抖把整列包进去。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出"看不见"，拿 ADMIN 跑负向是恒真的假绿。
 *
 * <p><b>如实说明</b>：本页拉列表的 `GET /email-campaigns` 挂的**也是** `email:manage`
 * （`EmailController.java:101-102`），所以生产环境里非持有者连列表都拉不出来（403），
 * 真实数据行根本不会出现。本文件把 `fetchEmailCampaigns` 替身化，才能在"无码"下渲染出数据行，
 * 把「测试不见了」与「页面没渲染」分开——反空洞守卫就是这个用途。第 ②/③ 例锁的是
 * **判据的正确性**（收口写法对得上码），不是生产里的可观测差异。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `email:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['email:manage'] }

/** 行姿态固定：判据只看权限码，本页**没有**业务状态/数据条件参与 gating（状态只影响 Tag 颜色与文案）。 */
const campaignRow = {
  id: 12,
  name: '老客户召回',
  status: 'DONE',
  totalCount: 20,
  sentCount: 18,
  failedCount: 2,
  openCount: 5,
  clickCount: 1,
}

const queryTestSend = () => screen.queryAllByText('pages.marketing.emailCampaign.btnTest')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchEmailCampaigns, fetchEmailTemplates } = await import('../../services/emailService')
  const { fetchSegments } = await import('../../services/segmentService')
  vi.mocked(fetchEmailCampaigns).mockResolvedValue([campaignRow] as never)
  vi.mocked(fetchEmailTemplates).mockResolvedValue([] as never)
  vi.mocked(fetchSegments).mockResolvedValue([] as never)

  renderWithProviders(<EmailCampaignPage />)
  // 反空洞守卫：等到这一行真的渲染出来，后面的否定断言才有意义 ——
  // 否则"「测试」不在"可能只是"表格还没加载出来"（渲染失败被读成"正确地不在"）。
  await screen.findByText('老客户召回', {}, { timeout: 5000 })
}

/** 反空洞守卫：页面确实渲染过，且逐条钉住**不受 `email:manage` 管辖**的相邻控件/列。 */
function expectPageRendered() {
  // 恒存在的锚点：页标题（ProTable headerTitle）
  expect(screen.getByText('pages.marketing.emailCampaign.title')).toBeInTheDocument()
  // 行数据
  expect(screen.getByText('老客户召回')).toBeInTheDocument()
  // 与权限无关的列头
  expect(screen.getByText('pages.marketing.emailCampaign.colName')).toBeInTheDocument()
  expect(screen.getByText('pages.marketing.emailCampaign.colStatus')).toBeInTheDocument()
  expect(screen.getByText('pages.marketing.emailCampaign.colRecipients')).toBeInTheDocument()
  // 动作列里刻意未收口的「记录」——同一格里的邻居，任何用户下都必须在
  expect(screen.getByText('pages.marketing.emailCampaign.btnRecords')).toBeInTheDocument()
  // 工具栏（搜索框 + 新建群发）同样不受该码管辖
  expect(screen.getByPlaceholderText('pages.marketing.emailCampaign.phTestEmail')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /pages\.marketing\.emailCampaign\.btnCreate/ })).toBeInTheDocument()
}

describe('EmailCampaignPage 行内「测试」的权限收口（087）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① 持 email:manage 的 SALES：行内「测试」可见', async () => {
    await renderPage(salesWithPerm)

    expectPageRendered()
    expect(queryTestSend()).toHaveLength(1)
  })

  it('② 零权限码的 SALES：页面照常渲染，「记录」仍在，唯独「测试」不可见', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryTestSend()).toHaveLength(0)
  })

  it('③ 只持同域但独立的 campaign:delete 不构成放行——「测试」仍旧不可见', async () => {
    await renderPage({ ...salesNoPerm, permissions: ['campaign:delete'] })

    expectPageRendered()
    expect(queryTestSend()).toHaveLength(0)
  })

  it('④ ADMIN：「测试」可见（ADMIN 由 hasPerm 直通，与码无关，只作正向锚点）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(queryTestSend()).toHaveLength(1)
  })

  it('⑤ 有码时「测试」确实是测试发送动作：未填测试邮箱即点，提示先输入邮箱', async () => {
    await renderPage(salesWithPerm)

    fireEvent.click(queryTestSend()[0])

    // 走的是 onTestSend 的空值分支（msgEnterTestEmail），说明这个链接就是测试发送，
    // 而不是一个只渲染出来、点了没反应的死链。
    expect(
      await screen.findByText('pages.marketing.emailCampaign.msgEnterTestEmail', {}, { timeout: 5000 }),
    ).toBeInTheDocument()
  })
})
