import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import KnowledgeArticleListPage from './KnowledgeArticleListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/knowledgeService', () => ({
  fetchArticles: vi.fn(),
  createArticle: vi.fn(),
  updateArticle: vi.fn(),
  deleteArticle: vi.fn(),
  publishArticle: vi.fn(),
  unpublishArticle: vi.fn(),
}))

/**
 * 086 权限收口 · `KnowledgeArticleListPage` 的**两个不同权限码**的渲染测试。
 *
 * <p>本页的判据值得单独一个文件，因为它是全批里少数几处**同一行内挂两个不同码**的页面：
 *
 * <pre>
 *   发布 / 下架  ← knowledge:update（KnowledgeArticleController 的 /publish、/unpublish）
 *   删除         ← knowledge:delete（同一 Controller 的 DELETE /knowledge/{id}）
 * </pre>
 *
 * <p>两者**不可互替**：持有 `knowledge:update` 的人不该看见删除链接，反之亦然。所以这里
 * 刻意安排了两个「只多一个码」的用户（`salesWithUpdate` / `salesWithDelete`），把差异收敛到
 * 唯一变量——只有这样才能把「按正确的码隐藏」与「整列没渲染出来」区分开。
 *
 * <p>发布/下架还带一层状态三元：`row.status === 'DRAFT'` 渲染「发布」，否则渲染「下架」。
 * 表格里同时放一行 DRAFT、一行 PUBLISHED，两个分支各断言一次。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const draftRow = {
  id: 1,
  title: '草稿文章',
  category: 'PRODUCT_USAGE',
  status: 'DRAFT',
  keywords: '入门',
  authorName: '张三',
  createdAt: '2026-01-01T00:00:00',
  version: 0,
}
const publishedRow = { ...draftRow, id: 2, title: '已发布文章', status: 'PUBLISHED', version: 1 }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `knowledge:update`，其余逐字相同。 */
const salesWithUpdate: UserInfo = { ...salesNoPerm, permissions: ['knowledge:update'] }
/** 只多了 `knowledge:delete`，其余逐字相同。 */
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['knowledge:delete'] }

const queryPublish = () => screen.queryAllByText('pages.knowledge.btnPublish')
const queryUnpublish = () => screen.queryAllByText('pages.knowledge.btnUnpublish')
const queryDelete = () => screen.queryAllByText('common.button.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchArticles } = await import('../../services/knowledgeService')
  vi.mocked(fetchArticles).mockResolvedValue({
    items: [draftRow, publishedRow],
    total: 2,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(<KnowledgeArticleListPage />)
  // 等到表格真的渲染出两行文章，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('草稿文章')
  await screen.findByText('已发布文章')
}

/** 反空洞守卫：两行数据（含两个不同状态）都真的落在表格里了。 */
function expectRowsRendered() {
  expect(screen.getByText('草稿文章')).toBeInTheDocument()
  expect(screen.getByText('已发布文章')).toBeInTheDocument()
  // 两个状态分支各自的状态标签也在：DRAFT 一行、PUBLISHED 一行
  expect(screen.getByText('pages.knowledge.statusDraft')).toBeInTheDocument()
  expect(screen.getByText('pages.knowledge.statusPublished')).toBeInTheDocument()
}

describe('KnowledgeArticleListPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「发布」「下架」「删除」', async () => {
    await renderPage(adminUser)

    expectRowsRendered()
    expect(queryPublish()).toHaveLength(1) // 只有 DRAFT 行
    expect(queryUnpublish()).toHaveLength(1) // 只有 PUBLISHED 行
    expect(queryDelete()).toHaveLength(2) // 两行各有删除
  })

  it('零权限的 SALES：行渲染出来了，但「发布」「下架」「删除」都不在', async () => {
    await renderPage(salesNoPerm)

    expectRowsRendered()
    expect(queryPublish()).toHaveLength(0)
    expect(queryUnpublish()).toHaveLength(0)
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 knowledge:update 的 SALES：看得见「发布」「下架」，看不见「删除」', async () => {
    await renderPage(salesWithUpdate)

    expectRowsRendered()
    expect(queryPublish()).toHaveLength(1)
    expect(queryUnpublish()).toHaveLength(1)
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 knowledge:delete 的 SALES：看得见「删除」，看不见「发布」「下架」', async () => {
    await renderPage(salesWithDelete)

    expectRowsRendered()
    expect(queryDelete()).toHaveLength(2)
    expect(queryPublish()).toHaveLength(0)
    expect(queryUnpublish()).toHaveLength(0)
  })
})
