import { expect, test, type Page } from '@playwright/test'
import { login } from './helpers/login'
import {
  LIVENESS,
  PIXEL_TOLERANCE_PX,
  VIEWPORT_WIDE,
  assertLiveness,
  docOverflowOf,
  expectAtMost,
  expectRelative,
  fillOf,
  measureLastRowReachability,
  overflowOf,
  readListPageGeometry,
  reportBucket,
  reportListPage,
  residualOf,
  waitForGeometryStable,
  type ListPageGeometry,
} from './helpers/geometry'

/**
 * 092 US1：**090「列表页内容区撑满」的几何不变式**（FR-EG-005/006/009/011/012）。
 *
 * <p><b>为什么必须有这道用例</b>：090 的判据全是**量出来的**，而它的判据今天**一条自动化用例都没有**
 * （jsdom 没有布局引擎，`clientWidth` / `getBoundingClientRect()` 全是假值）。
 * 于是任何一次外壳或 CSS 改动都能悄悄把几何改回去，而**全仓测试仍然全绿**——
 * 这正是 091 那个缺陷活了半年的同一台机器。
 *
 * <p><b>判据一律相对量</b>：页脚高度、卡片下外边距都在用例里**现量**，
 * 不写死 `664` / `624` / `56`。那些数只作对照读数出现在注释与 `quickstart.md` 里。
 *
 * <p><b>短/长页运行时判定</b>：依据「滚动盒是否溢出」，**不写死路径清单**——
 * `/customers` 在两次探针之间就从 19 行变成 20 行。样本数有**硬下限**，零样本 = 红。
 */
test.use({ viewport: VIEWPORT_WIDE })

// 每条用例要登录 + 逐页加载 4~6 个页面，比默认 30s 紧，故显式放宽
test.describe.configure({ timeout: 60_000 })

/**
 * 宽屏短页候选（4 页）。**两个入口各有样本**，缺哪个入口都会红：
 * - `.page-fade > .ant-pro-table > …`（入口 A）⇒ `/quotas`、`/orders`
 * - `.page-fade > .ant-card`（入口 B）⇒ `/data-retention`、`/departments`
 */
const CANDIDATES = ['/quotas', '/orders', '/data-retention', '/departments', '/users', '/customers']

type Buckets = { short: ListPageGeometry[]; long: ListPageGeometry[] }

/**
 * 逐页加载 → 两阶段等待 → 量取 → **运行时**按「滚动盒是否溢出」分桶。
 *
 * <p>每个候选页都断言「页面根卡片找得到」：选择器失效或页面结构变了 ⇒ **红**，
 * 不得静默地把这一页丢出桶外（`/recycle` 曾经就是这样——它实测渲染的是 404 页，
 * 根本没有根卡片，那次是**真的核对**才发现的）。
 */
async function sweep(page: Page, paths: string[]): Promise<Buckets> {
  const short: ListPageGeometry[] = []
  const long: ListPageGeometry[] = []
  for (const path of paths) {
    await page.goto(path)
    await waitForGeometryStable(page, { requireRootCard: true })
    const g = await readListPageGeometry(page)
    reportListPage(g, '列表页')
    expect
      .soft(
        g.cardKind,
        `[${path}] 未找到页面根卡片：第 ${g.rows} 行、滚动盒底边 ${g.scrollBottom}。` +
          `选择器（.page-fade > .ant-pro-table > .ant-pro-card:not(.ant-pro-table-search)` +
          ` / .page-fade > div > … / .page-fade > .ant-card）已失效，或该路由不再是列表页。`,
      )
      .not.toBeNull()
    ;(overflowOf(g) > 0 ? long : short).push(g)
  }
  return { short, long }
}

/** 打印分类结论（SC-EG-008：人不必读源码就能核对「它到底测了什么」）。 */
function reportClassification(b: Buckets): void {
  console.log(
    `[092][分类] 短页 ${b.short.length} 页：${b.short.map((g) => g.path).join(', ') || '（空）'}` +
      ` ｜ 长页 ${b.long.length} 页：${b.long.map((g) => `${g.path}(溢出 ${overflowOf(g)}, ${g.rows} 行)`).join(', ') || '（空）'}`,
  )
}

test.describe('090 列表页几何：卡片吃满内容区（1280×720）', () => {
  test('短页：卡片吃满内容区，余量只剩可解释的外壳占位', async ({ page }) => {
    await login(page)
    const b = await sweep(page, CANDIDATES)
    reportClassification(b)

    for (const g of b.short) {
      // FR-EG-005：卡片底边落在内容盒底边上（余量为 0）
      expectRelative({ page: g.path, quantity: 'fill = 滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距' }, fillOf(g), 0, PIXEL_TOLERANCE_PX)
      // FR-EG-006：除「页脚 + 内容区下内边距 + 卡片下外边距」这笔可解释的外壳占位外，没有多余空白
      expectRelative({ page: g.path, quantity: 'residual = (视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片下外边距)' }, residualOf(g), 0, PIXEL_TOLERANCE_PX)
      // US1-①后半：不出现幽灵滚动
      expectRelative({ page: g.path, quantity: '幽灵滚动 = 滚动盒 scrollHeight − clientHeight' }, overflowOf(g), 0, PIXEL_TOLERANCE_PX)
    }

    // FR-EG-010 / SC-EG-002：样本数**有下限**，零样本 = 红；两个入口各有样本
    reportBucket('宽屏短页', b.short.map((g) => g.path), LIVENESS.wideShort)
    assertLiveness(
      '宽屏短页',
      b.short.map((g) => g.path),
      LIVENESS.wideShort,
      '短页样本归零通常是**数据增长把短页撑成了长页**（预期行为），处置是**增补采样页**，不是放宽判据。',
    )
    const proTable = b.short.filter((g) => g.cardKind === 'pro-table')
    const cardRoot = b.short.filter((g) => g.cardKind === 'card')
    reportBucket('宽屏短页 · ProTable 根入口（090 入口 A）', proTable.map((g) => g.path), LIVENESS.wideShortProTableRoot)
    reportBucket('宽屏短页 · Card 根入口（090 入口 B）', cardRoot.map((g) => g.path), LIVENESS.wideShortCardRoot)
    assertLiveness(
      '宽屏短页 · ProTable 根入口',
      proTable.map((g) => g.path),
      LIVENESS.wideShortProTableRoot,
      '入口 A 在 index.css 的撑满规则里没有样本 —— 那个入口的回归会失去守护。',
    )
    assertLiveness(
      '宽屏短页 · Card 根入口',
      cardRoot.map((g) => g.path),
      LIVENESS.wideShortCardRoot,
      '入口 B 在 index.css 的撑满规则里没有样本 —— 那个入口的回归会失去守护。',
    )
  })

  test('长页：按内容撑高，不得断言填满（FR-EG-012）', async ({ page }) => {
    await login(page)
    const b = await sweep(page, CANDIDATES)

    for (const g of b.long) {
      // ① 确实溢出（内容把页面撑高了）
      expect
        .soft(
          overflowOf(g),
          `[${g.path}] 溢出 = 滚动盒 scrollHeight − clientHeight：实测 ${overflowOf(g)}，应达 > 0（长页必须确实溢出）`,
        )
        .toBeGreaterThan(0)
      // ② fill ≤ 0：卡片**不得**反而缩回去（缩回去就是「被压扁」那种坏法）
      expectAtMost({ page: g.path, quantity: 'fill（长页的上界）' }, fillOf(g), 0, '≤ 0（不得缩回去）')
      // ③ 末行完整可见（不被 .ant-table{overflow:hidden} 裁掉）
      const reach = await measureLastRowReachability(page)
      expect(reach.rows, `[${g.path}] 未找到表格行（${'.ant-table-tbody > tr.ant-table-row'}）`).toBeGreaterThan(0)
      expect
        .soft(
          reach.lastRowOverflow ?? Number.POSITIVE_INFINITY,
          `[${g.path}] 末行可见性：滚到底后末行超出可见内容区 ${reach.lastRowOverflow}px，应达 ≤ ${PIXEL_TOLERANCE_PX}px（共 ${reach.rows} 行）`,
        )
        .toBeLessThanOrEqual(PIXEL_TOLERANCE_PX)
    }

    reportBucket('宽屏长页', b.long.map((g) => g.path), LIVENESS.wideLong)
    assertLiveness(
      '宽屏长页',
      b.long.map((g) => g.path),
      LIVENESS.wideLong,
      '长页样本归零通常是**数据被清空**（预期行为，见 research §8 第 2 条），处置是增补采样页。',
    )
  })

  test('横向溢出与分类报告：页面级左右晃动恒为 0（FR-EG-009 / SC-EG-008）', async ({ page }) => {
    await login(page)
    const b = await sweep(page, CANDIDATES)
    reportClassification(b)

    for (const g of [...b.short, ...b.long]) {
      expectRelative({ page: g.path, quantity: '页面级横向溢出 = documentElement.scrollWidth − innerWidth' }, docOverflowOf(g), 0, PIXEL_TOLERANCE_PX)
    }
  })
})
