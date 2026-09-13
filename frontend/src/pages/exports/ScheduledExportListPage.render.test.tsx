import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ScheduledExportListPage from './ScheduledExportListPage'
import { scheduledExportApi } from '../../services/api/scheduledExportApi'
import { useAuthStore } from '../../store/authStore'

vi.mock('../../services/api/scheduledExportApi', () => ({
  scheduledExportApi: { list: vi.fn(async () => []), delete: vi.fn(), updateStatus: vi.fn() },
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

/**
 * 列表页「标题卡」的渲染冒烟——与 `DataRetentionPolicyListPage.render.test.tsx` 同因同形。
 *
 * <p>改造前标题是一个没有孩子的 `<Card title={…} />` 塞在 `Space` 里，渲染成「只写着标题的空
 * 边框」，表格在卡外。这里断言结构（标题在卡头、表格在卡内、按钮在卡头 `extra`），
 * 因为文案层面的断言（标题存在）在那处缺陷下**照样是绿的**。
 */
describe('ScheduledExportListPage（列表页标题卡结构）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
    vi.mocked(scheduledExportApi.list).mockResolvedValue([])
  })

  it('标题与新建按钮在同一张 Card 的头部，表格也在卡内', async () => {
    renderWithProviders(<ScheduledExportListPage />)

    const title = await screen.findByText('pages.scheduledExport.list.title')
    const head = title.closest('.ant-card-head')
    expect(head, '标题应渲染在 Card 头部').not.toBeNull()
    expect(
      title.closest('.ant-card')?.querySelector('.ant-table'),
      '表格必须与标题同在一张 Card 内——改造前标题是卡外的一只空边框，此处查不到 .ant-table',
    ).not.toBeNull()
    expect(head?.textContent).toContain('pages.scheduledExport.common.createTask')
  })

  it('登录身份就绪后按当前用户拉列表（不是写死的 1 号）', async () => {
    renderWithProviders(<ScheduledExportListPage />)

    await screen.findByText('pages.scheduledExport.list.title')
    expect(scheduledExportApi.list).toHaveBeenCalledWith(1)
  })
})
