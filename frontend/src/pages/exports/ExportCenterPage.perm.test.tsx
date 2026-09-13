import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ExportCenterPage from './ExportCenterPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/exportService', () => ({
  fetchExportJobs: vi.fn(),
  createExportJob: vi.fn(),
  downloadExportJob: vi.fn(),
}))

/**
 * 086 权限收口 · `ExportCenterPage` 的「导出」按钮渲染测试。
 *
 * <p><b>本页刻意有一个反例，务必分清：</b>
 * <pre>
 *   工具栏「导出」  ← 收了，export:create（POST /exports，ExportController.java:52-53）
 *   行内「下载」    ← **刻意不收**（GET /exports/{id}/download，ExportController.java:67）
 * </pre>
 * 下载端点在**后端没有任何权限注解**，前端硬挂 `export:create` 会造成一次真实收窄：
 * 持码者之外的人本可以下载却被前端挡掉（详见 specs/086-frontend-button-gating/research.md）。
 * 因此本文件对「下载」只做**正向**断言（无码用户仍然看得见），绝不写"无码不可见"。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const doneJob = {
  id: 1,
  exportType: 'LEAD',
  status: 'DONE',
  rowCount: 10,
  fileName: 'leads-2026.xlsx',
  createdAt: '2026-01-01T00:00:00',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `export:create`，其余逐字相同。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['export:create'] }

const queryExportButton = () => screen.queryByRole('button', { name: /pages\.exportCenter\.btnExport/ })
const queryDownloadLink = () => screen.queryByText('pages.exportCenter.btnDownload')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchExportJobs } = await import('../../services/exportService')
  vi.mocked(fetchExportJobs).mockResolvedValue({
    items: [doneJob],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(<ExportCenterPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('leads-2026.xlsx')
}

/** 反空洞守卫：表格行、类型枚举、以及未收口的「下载」都在。 */
function expectPageRendered() {
  expect(screen.getByText('leads-2026.xlsx')).toBeInTheDocument()
  // `enums.entity.lead` 会出现两次：工具栏 Select 的选中项 + 表格单元格，故用 getAllByText
  expect(screen.getAllByText('enums.entity.lead').length).toBeGreaterThan(0)
  // 「下载」不收口：这是有意的正向断言，证明我们没有把它一起收窄
  expect(queryDownloadLink()).toBeInTheDocument()
}

describe('ExportCenterPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「导出」', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(queryExportButton()).toBeInTheDocument()
  })

  it('无 export:create 的 SALES：页面渲染出来了，「导出」不在，「下载」仍在（下载刻意不收）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryExportButton()).not.toBeInTheDocument()
  })

  it('持有 export:create 的 SALES：看得见「导出」（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expectPageRendered()
    expect(queryExportButton()).toBeInTheDocument()
  })
})
