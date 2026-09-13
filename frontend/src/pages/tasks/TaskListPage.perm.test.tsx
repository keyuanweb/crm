import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import TaskListPage from './TaskListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { TaskStatus } from '../../types/task'

vi.mock('../../services/taskService', () => ({
  fetchTasks: vi.fn(),
  fetchReminderSummary: vi.fn(async () => ({ overdueCount: 0, todayCount: 0, todoCount: 0 })),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  toggleTask: vi.fn(),
  deleteTask: vi.fn(),
  fetchTaskCalendar: vi.fn(),
}))

/**
 * 086 权限收口 · `TaskListPage` 的**两码互不串码**测试。
 *
 * <p>行内的两个动作打两个端点、两个码，且**都只挂在行内**：
 *
 * <pre>
 *   完成 / 重开（按 status 二选一）  POST   /tasks/{id}/toggle  → task:update
 *   删除                            DELETE /tasks/{id}         → task:delete
 * </pre>
 *
 * <p>同页两码 ⇒ 必须证明「持 A 码不持 B 码时只有 A 的控件可见」。否则两码共用同一个
 * `usePerms` 结果对象，把 `can[PERMS.taskDelete]` 误写成 `can[PERMS.taskUpdate]` 这类接线错误
 * 也能全绿——单码页的断言结构抓不到它。
 *
 * <p>注意「完成 / 重开」是**同一个码的两个状态分支**（`TaskListPage.tsx:215-223`），
 * 不是两个码：断言要按行状态分别取值，`TODO` 行看「完成」、`DONE` 行看「重开」。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const TASK_TITLE = '回访 Acme 科技'

const makeTask = (status: TaskStatus) => ({
  id: 1,
  title: TASK_TITLE,
  dueAt: '2026-09-20T18:00:00',
  priority: 'HIGH',
  status,
  reminderStatus: 'NORMAL',
  version: 0,
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }

/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量，这就是「互不串码」的取法。 */
const salesUpdateOnly: UserInfo = { ...salesNoPerm, permissions: ['task:update'] }
const salesDeleteOnly: UserInfo = { ...salesNoPerm, permissions: ['task:delete'] }

/** 「完成」（TODO 行）与「重开」（DONE 行）——同一个码的两个状态分支。 */
const linkDone = () => screen.queryByText('pages.task.list.done')
const linkRedo = () => screen.queryByText('pages.task.list.redo')
const linkDelete = () => screen.queryByText('pages.task.list.delete')
/** 「编辑」刻意不在收口范围内（086 只收删除类与终态变更），三条用例里它都必须一直在。 */
const linkEdit = () => screen.queryByText('pages.task.list.edit')

async function renderPage(user: UserInfo, status: TaskStatus = 'TODO') {
  useAuthStore.setState({ user })
  const { fetchTasks } = await import('../../services/taskService')
  vi.mocked(fetchTasks).mockResolvedValue({ items: [makeTask(status)], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<TaskListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText(TASK_TITLE, {}, { timeout: 5000 })
}

describe('TaskListPage 两码收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：完成 / 重开 / 删除 全都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser, 'TODO')

    expect(linkDone()).toBeInTheDocument()
    expect(linkDelete()).toBeInTheDocument()
    expect(linkEdit()).toBeInTheDocument()
  })

  it('② 零权限的 SALES：完成 / 重开 / 删除 全不可见（先确认页面已渲染）', async () => {
    await renderPage(salesNoPerm, 'TODO')

    // 反空洞守卫：任务行确实渲染出来了，否定断言才不是假绿
    expect(screen.getByText(TASK_TITLE)).toBeInTheDocument()

    expect(linkDone()).not.toBeInTheDocument()
    expect(linkDelete()).not.toBeInTheDocument()
    // 「编辑」不在收口范围内，必须**照旧可见**——这条同时证明第 ② 例不是「整页没渲染」
    expect(linkEdit()).toBeInTheDocument()
  })

  it('③ 持 task:update 的 SALES：完成可见、删除不可见（互不串码）', async () => {
    await renderPage(salesUpdateOnly, 'TODO')

    expect(screen.getByText(TASK_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(linkDone()).toBeInTheDocument()
    // 本批的核心价值：task:update 不会顺带放行 task:delete 管着的控件
    expect(linkDelete()).not.toBeInTheDocument()
  })

  it('④ 持 task:delete 的 SALES：删除可见、完成不可见（互不串码反向）', async () => {
    await renderPage(salesDeleteOnly, 'TODO')

    expect(screen.getByText(TASK_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(linkDelete()).toBeInTheDocument()

    expect(linkDone()).not.toBeInTheDocument()
  })

  it('⑤ 持 task:update 的 SALES：DONE 行上「重开」可见（同码的另一个状态分支）', async () => {
    await renderPage(salesUpdateOnly, 'DONE')

    expect(screen.getByText(TASK_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(linkRedo()).toBeInTheDocument()
    // 同一行的「完成」不该出现——状态判断与权限判断是两件事
    expect(linkDone()).not.toBeInTheDocument()
  })

  it('⑥ 零权限的 SALES：DONE 行上「重开」也不可见', async () => {
    await renderPage(salesNoPerm, 'DONE')

    expect(screen.getByText(TASK_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(linkRedo()).not.toBeInTheDocument()
  })
})
