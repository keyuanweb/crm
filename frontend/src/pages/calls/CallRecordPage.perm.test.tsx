import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CallRecordPage from './CallRecordPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/callRecordService', () => ({
  fetchCallRecords: vi.fn(),
  fetchCallStats: vi.fn(async () => ({
    totalCount: 0,
    totalDurationSeconds: 0,
    avgDurationSeconds: 0,
    byDirection: [],
  })),
  createCallRecord: vi.fn(),
  updateCallRecord: vi.fn(),
  deleteCallRecord: vi.fn(),
}))
// 页内的 CustomerSelect（仅在弹窗里）会去拉客户下拉；弹窗首帧不开，但不能留未打桩的 service。
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
  fetchCustomer: vi.fn(),
  fetchAtRiskCustomers: vi.fn(),
}))

/**
 * 086 权限收口 · `CallRecordPage` 行内「删除」的渲染测试。
 *
 * <p>本页行内只有两个动作，只有删除被收口（`CallRecordPage.tsx:95-99`）：
 *
 * <pre>
 *   编辑   —— 不在收口范围内，恒可见
 *   删除   DELETE /call-records/{id} → call_record:delete（CallRecordController）
 * </pre>
 *
 * <p>「编辑」恒可见这一点在本页特别有用：它与删除是**同一格里的两个兄弟**，所以
 * 「无码 ⇒ 这一格整格不渲染」这个常见假绿解释被直接排除——格子在、编辑在，只有删除不在。
 * 故三条用例里都同时断言「编辑可见」，它既是反空洞守卫，也是「删除的消失是权限造成」的对照。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const CUSTOMER_NAME = 'Acme 科技'

const callRow = {
  id: 1,
  customerId: 10,
  customerName: CUSTOMER_NAME,
  contactName: '张三',
  direction: 'OUTBOUND',
  durationSeconds: 125,
  result: 'CONNECTED',
  recordedAt: '2026-09-01T10:00:00',
  remark: '初次沟通',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多这一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['call_record:delete'] }

const linkDelete = () => screen.queryByText('common.button.delete')
const linkEdit = () => screen.queryByText('common.button.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCallRecords } = await import('../../services/callRecordService')
  vi.mocked(fetchCallRecords).mockResolvedValue({ items: [callRow], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<CallRecordPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText(CUSTOMER_NAME, {}, { timeout: 5000 })
}

describe('CallRecordPage 行内删除收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：编辑与删除都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser)

    expect(linkEdit()).toBeInTheDocument()
    expect(linkDelete()).toBeInTheDocument()
  })

  it('② 无 call_record:delete 的 SALES：删除不可见，同格的「编辑」照旧可见（先确认页面已渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：通话记录行确实渲染出来了，否定断言才不是假绿
    expect(screen.getByText(CUSTOMER_NAME)).toBeInTheDocument()
    expect(screen.getByText('张三')).toBeInTheDocument()

    // 同一格里的兄弟控件仍在 → 排除了「整格/整页没渲染」这个假绿解释
    expect(linkEdit()).toBeInTheDocument()
    expect(linkDelete()).not.toBeInTheDocument()
  })

  it('③ 持 call_record:delete 的 SALES：删除可见（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expect(linkEdit()).toBeInTheDocument()
    expect(linkDelete()).toBeInTheDocument()
  })
})
