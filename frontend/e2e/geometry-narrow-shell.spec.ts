import { expect, test, type Page } from '@playwright/test'
import { login } from './helpers/login'
import {
  LIVENESS,
  NARROW_CONTENT_INSET_PX,
  PIXEL_TOLERANCE_PX,
  SCROLLBAR_TOLERANCE_PX,
  VIEWPORT_NARROW,
  assertLiveness,
  expectRelative,
  readNarrowShellGeometry,
  reportBucket,
  reportNarrowPage,
  waitForGeometryStable,
  type NarrowShellGeometry,
} from './helpers/geometry'

/**
 * 092 US2：**091「窄屏外壳塌陷」的几何不变式**（FR-EG-007/008/009）。
 *
 * <p><b>为什么必须有这道用例</b>：091 的窄屏几何**今天没有任何东西守着**——
 * 单测只能断言「使布局塌陷的那个类名在不在」（**结构前提**），**量不了宽度**。
 * 也就是说：只要那个类名不被引入，内容区是 **375** 还是 **24**，单测**看不出来**。
 * 而 24px 的缝 = 全站**不可用**。
 *
 * <p><b>⚠️ 两个量必须分开断言</b>（FR-EG-007）：
 * - **内容区可见宽度** = `.page-scroll` 的 `clientWidth`（**含**其自身左右内边距）⇒ 375
 * - **内容容器宽度** = `.page-container` 的内容盒宽 ⇒ 351
 *
 * 只断言「内容容器宽 = 内容区可见宽 − 24」会被**缺陷态同时满足**（`0 = 24 − 24`）——
 * 那是一条**会放过原缺陷**的假判据。`specs/091-narrow-shell-collapse/spec.md` 的「订正块」
 * 正是因为把两者混为一句而订正过一次。
 *
 * <p><b>不用 `matchMedia` 判定窄屏</b>：`src/test/setup.ts` 把它桩成恒 `false`（那是单测的事）；
 * 为免两套口径混用，这里一律用**显式视口**。
 */
test.use({ viewport: VIEWPORT_NARROW })

// 每条用例要登录 + 逐页加载 4 个页面，比默认 30s 紧，故显式放宽
test.describe.configure({ timeout: 60_000 })

/**
 * 窄屏采样四页——与 091 取证用的是**同一组**，保持可比。
 * 其中 `/stats` 是仪表盘（**没有**页面根卡片），故等待阶段不要求根卡片、要求菜单在场。
 */
const PAGES = ['/stats', '/customers', '/orders', '/roles']

async function sweep(page: Page): Promise<NarrowShellGeometry[]> {
  const out: NarrowShellGeometry[] = []
  for (const path of PAGES) {
    await page.goto(path)
    await waitForGeometryStable(page, { requireRootCard: false, requireMenu: true })
    const g = await readNarrowShellGeometry(page)
    reportNarrowPage(g)
    out.push(g)
  }
  return out
}

test.describe('091 窄屏外壳几何（375×812）', () => {
  test('内容区可见宽度 = 视口宽（独立断言一，FR-EG-007 前半）', async ({ page }) => {
    await login(page)
    const all = await sweep(page)

    for (const g of all) {
      // 容差只留给滚动条占位（SCROLLBAR_TOLERANCE_PX 写死并注明理由：本机实测 0，防 CI 换实现后假红）。
      // 缺陷态是 24，与 375 差 351 —— 比容差大一个数量级以上，不会被放过。
      const viewportWidth = VIEWPORT_NARROW.width
      expectRelative(
        { page: g.path, quantity: '内容区可见宽度（.page-scroll 的 clientWidth，含其自身左右内边距）' },
        g.contentVisibleWidth,
        viewportWidth,
        SCROLLBAR_TOLERANCE_PX,
        `视口宽 ${viewportWidth}（滚动条容差 ±${SCROLLBAR_TOLERANCE_PX}px）`,
      )
    }

    reportBucket('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow)
    assertLiveness('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow, '窄屏判据与数据无关，样本不应归零。')
  })

  test('内容容器宽度 = 内容区可见宽度 − 24（独立断言二，FR-EG-007 后半）', async ({ page }) => {
    await login(page)
    const all = await sweep(page)

    for (const g of all) {
      // ⚠️ 这一条**不得**与上一条合并。它单独看**会被缺陷态满足**（0 = 24 − 24），
      // 故它存在的意义是**钉住口径**（351 这个数），抓住缺陷的是上一条。
      const expected = g.contentVisibleWidth - NARROW_CONTENT_INSET_PX
      expectRelative(
        { page: g.path, quantity: `内容容器宽度（.page-container 的内容盒宽，应 = 内容区可见宽 − ${NARROW_CONTENT_INSET_PX}）` },
        g.contentContainerWidth,
        expected,
        PIXEL_TOLERANCE_PX,
        `${g.contentVisibleWidth} − ${NARROW_CONTENT_INSET_PX} = ${expected}（±${PIXEL_TOLERANCE_PX}px）` +
          `；实测内容区左右内边距 ${g.scrollPadLeft}/${g.scrollPadRight}`,
      )
    }

    reportBucket('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow)
    assertLiveness('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow, '窄屏判据与数据无关，样本不应归零。')
  })

  test('菜单容器：宽 = 内容区可见宽、高 = 48，且锚点认对（FR-EG-008）', async ({ page }) => {
    await login(page)
    const all = await sweep(page)

    for (const g of all) {
      // 先证明「认对了对象」：菜单容器必须是同时承载内容区的那层外壳布局节点的直接子元素。
      // 认错时失败信息直接说清，而不是给出一个莫名其妙的小数值。
      expect.soft(g.menuAnchorOk, `[${g.path}] 菜单容器锚点：${g.menuAnchorReason}`).toBe(true)
      expectRelative(
        { page: g.path, quantity: '菜单容器宽（= 内容区可见宽，**不是** 内容容器宽）' },
        g.menuWidth,
        g.contentVisibleWidth,
        PIXEL_TOLERANCE_PX,
      )
      expectRelative({ page: g.path, quantity: '菜单容器高（其声明高度）' }, g.menuHeight, 48, PIXEL_TOLERANCE_PX)
    }

    reportBucket('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow)
    assertLiveness('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow, '窄屏判据与数据无关，样本不应归零。')
  })

  test('页面级横向溢出恒为 0，且四个量可读（FR-EG-009 / SC-EG-003 / SC-EG-008）', async ({ page }) => {
    await login(page)
    const all = await sweep(page)

    for (const g of all) {
      expectRelative(
        { page: g.path, quantity: '页面级横向溢出 = documentElement.scrollWidth − innerWidth' },
        g.docOverflow,
        0,
        PIXEL_TOLERANCE_PX,
      )
    }
    console.log(
      `[092][窄屏汇总] 内容区可见宽 ${all.map((g) => g.contentVisibleWidth).join('/')}` +
        ` ｜ 内容容器宽 ${all.map((g) => g.contentContainerWidth).join('/')}` +
        ` ｜ 菜单宽 ${all.map((g) => g.menuWidth).join('/')}` +
        ` ｜ 菜单高 ${all.map((g) => g.menuHeight).join('/')}`,
    )

    reportBucket('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow)
    assertLiveness('窄屏采样页', all.map((g) => g.path), LIVENESS.narrow, '窄屏判据与数据无关，样本不应归零。')
  })
})
