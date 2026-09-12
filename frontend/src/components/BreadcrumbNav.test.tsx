import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import BreadcrumbNav from './BreadcrumbNav'

/**
 * 面包屑渲染（084 收口）。
 *
 * <p>纯解析逻辑在 `breadcrumbTrail.test.ts` 里以分段断言；本文件只钉**渲染这一层**：
 * 分段 → 链接/纯文本、以及文案取哪条路径。两件事在别处看不到：
 *
 * <p>① 文案走 `menu.*` 并由 `menuLabel` 缺键降级。测试环境里 react-i18next 被全局打桩成
 * `t(key) => key`（`src/test/setup.ts`，缺键会抛错），于是 `menu.*` 必然等于键名、必然走降级分支——
 * 也就是说这里断言出的中文名正是**降级路径真的生效**的证据，而不是「恰好 t() 返回了中文」。
 * 非 `menu.*` 的两段（`breadcrumb.detail` / `breadcrumb.currentPage`）没有降级来源，
 * 因此断言的是键名本身（它们在 zh-CN/en 里都存在，缺了会由那个打桩抛错）。
 *
 * <p>② 置顶项（`/stats`）不渲染指向自身的链接，而其余路径的首段「首页」是链接、指向 `/stats`。
 */
function renderAt(route: string) {
  renderWithProviders(<BreadcrumbNav />, { route })
}

/** antd 的 Breadcrumb 会把每一段渲染成 `li`。 */
function crumbs(): string[] {
  return screen
    .getAllByRole('listitem')
    .map((node) => node.textContent ?? '')
}

describe('BreadcrumbNav', () => {
  it('商机阶段显示「首页 / 流程配置 / 商机阶段」，且末段可点回该页', () => {
    renderAt('/opportunity-stages')

    expect(crumbs()).toEqual(['首页', '流程配置', '商机阶段'])
    expect(screen.getByRole('link', { name: '首页' })).toHaveAttribute('href', '/stats')
    expect(screen.getByRole('link', { name: '商机阶段' })).toHaveAttribute(
      'href',
      '/opportunity-stages',
    )
  })

  it('分组名与侧边栏一致（配置类页面是「流程配置」而非「系统管理」）', () => {
    renderAt('/workflows')
    expect(crumbs()).toEqual(['首页', '流程配置', '工作流'])
  })

  it('详情页追加「详情」，且最后一段不带链接', () => {
    renderAt('/customers/123')

    expect(crumbs()).toEqual(['首页', '客户管理', '客户', 'breadcrumb.detail'])
    // 「详情」不是链接：它不对应任何路由
    expect(screen.queryByRole('link', { name: 'breadcrumb.detail' })).toBeNull()
  })

  it('数据大屏的中文名来自清单文案键，不是中文裸字面量', () => {
    renderAt('/data-vision')
    expect(crumbs()).toEqual(['首页', '数据分析', '数据大屏'])
  })

  it('置顶项自身只渲染一段，且不是链接（不在当前页上放一个指向自己的链接）', () => {
    renderAt('/stats')

    expect(crumbs()).toEqual(['首页'])
    expect(screen.queryByRole('link')).toBeNull()
  })

  it('不属于任何菜单项的路径显示「首页 / 当前页面」（保持改造前行为）', () => {
    renderAt('/account/password')
    expect(crumbs()).toEqual(['首页', 'breadcrumb.currentPage'])
  })
})
