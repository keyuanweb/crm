import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DataRetentionPolicyListPage from './DataRetentionPolicyListPage'
import { dataRetentionApi } from '../../services/api/dataRetentionApi'

vi.mock('../../services/api/dataRetentionApi', () => ({
  dataRetentionApi: { getAllPolicies: vi.fn(async () => []), deletePolicy: vi.fn() },
}))

/**
 * 列表页「标题卡」的渲染冒烟。
 *
 * <p><b>为什么值得一条用例</b>：改造前这一页的标题是一个**没有孩子的** `<Card title={…} />`，
 * 却被塞进 `<Space>` 与两个按钮并排——渲染出来是「一条只写着标题的空边框贴在按钮左边」，
 * 而 `style={{ flex: 1 }}` 落在 antd 的 `ant-space-item` 包装层内部，也不起作用。这处形态缺陷
 * 是人在浏览器里看出来的，任何既有断言都不会变红（单测里没有这一页，e2e 只探状态码）。
 * 因此这里断言的不是文案，而是**结构**：标题在 Card 头部、表格与标题同在一张卡内。
 */
describe('DataRetentionPolicyListPage（列表页标题卡结构）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dataRetentionApi.getAllPolicies).mockResolvedValue([])
  })

  it('标题与两个操作按钮在同一张 Card 的头部，表格也在卡内', async () => {
    renderWithProviders(<DataRetentionPolicyListPage />)

    const title = await screen.findByText('pages.dataRetention.list.title')
    const head = title.closest('.ant-card-head')
    expect(head, '标题应渲染在 Card 头部（改造前那张空 Card 也是，故这条只能算前提）').not.toBeNull()
    expect(
      title.closest('.ant-card')?.querySelector('.ant-table'),
      '表格必须与标题同在一张 Card 内——改造前标题是卡外的一只空边框，此处查不到 .ant-table',
    ).not.toBeNull()

    // `extra` 里的两个按钮也在这张卡的头部，而不是卡外另起一行
    expect(head?.textContent).toContain('pages.dataRetention.list.complianceExport')
    expect(head?.textContent).toContain('pages.dataRetention.common.createPolicy')
  })

  it('策略按接口返回值渲染到表格里', async () => {
    vi.mocked(dataRetentionApi.getAllPolicies).mockResolvedValue([
      {
        id: 7,
        entityType: 'CUSTOMER',
        retentionDays: 30,
        actionType: 'ARCHIVE',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00',
      } as never,
    ])

    renderWithProviders(<DataRetentionPolicyListPage />)

    expect(
      await screen.findByText('pages.dataRetention.common.entityTypeLabels.CUSTOMER'),
    ).toBeInTheDocument()
    expect(screen.getByText('pages.dataRetention.common.policyStatusLabels.ACTIVE')).toBeInTheDocument()
  })
})
