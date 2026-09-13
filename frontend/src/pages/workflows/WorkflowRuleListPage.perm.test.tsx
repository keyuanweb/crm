import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import WorkflowRuleListPage from './WorkflowRuleListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/workflowService', () => ({
  fetchWorkflowRules: vi.fn(),
  createWorkflowRule: vi.fn(),
  updateWorkflowRule: vi.fn(),
  toggleWorkflowRule: vi.fn(),
  deleteWorkflowRule: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
}))

/**
 * 086 权限收口 · `WorkflowRuleListPage` 的**两码互不串码**测试。
 *
 * <p>行内两个动作打两个端点、两个码（`WorkflowController.java:92-100`）：
 *
 * <pre>
 *   停用 / 启用（按 enabled 二选一）  POST   /workflows/rules/{id}/toggle  → workflow:update
 *   删除                            DELETE /workflows/rules/{id}         → workflow:delete
 * </pre>
 *
 * <p>两码都在同一个 `usePerms([...])` 里取值（`WorkflowRuleListPage.tsx:49`），所以
 * `can[PERMS.workflowUpdate]` 与 `can[PERMS.workflowDelete]` 极易被写成同一个键。互不串码断言
 * （③④）盯的正是这件事：只持 A 码时必须只有 A 的控件出现。
 *
 * <p>「停用 / 启用」是**同一个码的两个状态分支**（`:194-198`，文案按 `row.enabled` 二选一），
 * 不是两个码——断言要按行状态分别取值。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const RULE_NAME = '新客户自动分配'

const makeRow = (enabled: boolean) => ({
  id: 1,
  name: RULE_NAME,
  eventType: 'CUSTOMER_CREATED',
  actionType: 'ASSIGN',
  action: { targetUserId: 5 },
  enabled,
  version: 0,
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const supportNoPerm: UserInfo = { id: 2, username: 'support01', displayName: '客服一', role: 'SUPPORT', permissions: [] }

/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量，这就是「互不串码」的取法。 */
const supportUpdateOnly: UserInfo = { ...supportNoPerm, permissions: ['workflow:update'] }
const supportDeleteOnly: UserInfo = { ...supportNoPerm, permissions: ['workflow:delete'] }

/** 行内两个动作的可见文本：启停按行状态二选一，删除恒为 `delete`。 */
const linkToggle = (enabled: boolean) =>
  screen.queryByText(enabled ? 'pages.workflowRule.disable' : 'pages.workflowRule.enable')
const linkDelete = () => screen.queryByText('pages.workflowRule.delete')
/** 「编辑」刻意不在收口范围内，每条用例里它都必须一直在（同时充当反空洞守卫）。 */
const linkEdit = () => screen.queryByText('pages.workflowRule.edit')

async function renderPage(user: UserInfo, enabled = true) {
  useAuthStore.setState({ user })
  const { fetchWorkflowRules } = await import('../../services/workflowService')
  vi.mocked(fetchWorkflowRules).mockResolvedValue({ items: [makeRow(enabled)], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<WorkflowRuleListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText(RULE_NAME, {}, { timeout: 5000 })
}

describe('WorkflowRuleListPage 两码收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：停用 / 删除 都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser, true)

    expect(linkToggle(true)).toBeInTheDocument()
    expect(linkDelete()).toBeInTheDocument()
    expect(linkEdit()).toBeInTheDocument()
  })

  it('② 零权限的 SUPPORT：停用 / 删除 全不可见（先确认页面已渲染）', async () => {
    await renderPage(supportNoPerm, true)

    // 反空洞守卫：规则行确实渲染出来了，否定断言才不是假绿
    expect(screen.getByText(RULE_NAME)).toBeInTheDocument()

    expect(linkToggle(true)).not.toBeInTheDocument()
    expect(linkDelete()).not.toBeInTheDocument()
    // 「编辑」不在收口范围内，必须**照旧可见**——这条同时证明第 ② 例不是「整页没渲染」
    expect(linkEdit()).toBeInTheDocument()
  })

  it('③ 持 workflow:update 的 SUPPORT：停用可见、删除不可见（互不串码）', async () => {
    await renderPage(supportUpdateOnly, true)

    expect(screen.getByText(RULE_NAME)).toBeInTheDocument() // 反空洞守卫
    expect(linkToggle(true)).toBeInTheDocument()
    // 本批的核心价值：workflow:update 不会顺带放行 workflow:delete 管着的控件
    expect(linkDelete()).not.toBeInTheDocument()
  })

  it('④ 持 workflow:delete 的 SUPPORT：删除可见、停用不可见（互不串码反向）', async () => {
    await renderPage(supportDeleteOnly, true)

    expect(screen.getByText(RULE_NAME)).toBeInTheDocument() // 反空洞守卫
    expect(linkDelete()).toBeInTheDocument()

    expect(linkToggle(true)).not.toBeInTheDocument()
  })

  it('⑤ 持 workflow:update 的 SUPPORT：停用中的规则显示「启用」（同码的另一个状态分支）', async () => {
    await renderPage(supportUpdateOnly, false)

    expect(screen.getByText(RULE_NAME)).toBeInTheDocument() // 反空洞守卫
    expect(linkToggle(false)).toBeInTheDocument()
    // 同一行的「停用」不该出现——状态判断与权限判断是两件事
    expect(linkToggle(true)).not.toBeInTheDocument()
  })
})
