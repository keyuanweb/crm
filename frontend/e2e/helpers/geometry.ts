import { expect, type Page } from '@playwright/test'

/**
 * 092 几何护栏的共享量取层：**两个 spec 的口径只能有这一份**。
 *
 * <p><b>为什么必须抽出来</b>：本仓刚因为「两个几何量共用一个名字」订正过一次
 * （见 `specs/091-narrow-shell-collapse/spec.md` 的「订正块」）。两份 spec 各写一份量取，
 * 就是让那类缺陷重新长出来的土壤，故这里把「量取表达式 + 等待策略 + 报告与活性断言」集中一处。
 *
 * <p><b>判据一律是相对量</b>（FR-EG-005/006）：页脚高度**现量**，卡片下外边距**现量**，
 * 绝不写死 `664` / `624` / `56`——那些数只作对照读数出现在注释里。
 * 理由：视口高、页脚高、内容区内边距三者会独立变化，写死任何一项，护栏都会在**无害**的调整上假红，
 * 而假红会让人去改护栏而不是查问题（`specs/092-geometry-e2e-guard/research.md` §1）。
 *
 * <p><b>⚠️ 两个必须分开断言的量</b>：
 * - 「**内容区可见宽度**」= `.page-scroll` 的 `clientWidth`（**含**其自身左右内边距）⇒ 窄屏 375
 * - 「**内容容器宽度**」= `.page-container` 的内容盒宽 ⇒ 窄屏 351
 *
 * 它们是**两个量**。只断言「内容容器宽 = 内容区可见宽 − 24」会被**缺陷态同时满足**
 * （`0 = 24 − 24`，而缺陷态的内容容器宽正是 0）——那是一条**会放过原缺陷的假判据**。
 * 故本文件不提供「算差值」的便捷函数，只提供两个独立的量，逼使用例分别断言。
 */

/** 宽屏视口：显式设定，**不依赖** Playwright 的默认值（默认值会随版本变）。 */
export const VIEWPORT_WIDE = { width: 1280, height: 720 } as const

/** 窄屏视口：091 取证用的是同一档，保持可比。 */
export const VIEWPORT_NARROW = { width: 375, height: 812 } as const

/**
 * 窄屏下内容区自身左右内边距之和 = **24**（各 12，来自 `App.tsx` 的 `padding: isMobile ? 12 : …`）。
 *
 * 它是 FR-EG-007 第二条断言里那个 `− 24` 的来源，也就是对照读数 **351 = 375 − 24** 的来历。
 *
 * <p><b>⚠️ 为什么它是常量而不是「现量的 `scrollPadLeft + scrollPadRight`」</b>：
 * 后者会让这条断言**退化成一个恒等式**——缺陷态下内容区可见宽是 24、左右内边距各 12，
 * 于是 `0 == 24 − 24` **成立**，原缺陷被放过。这与 FR-EG-007 明令禁止的「只断言两者之差」
 * 是同一个坑，只是换了个形式。写死 24 之后，这条断言才真的在钉「351」这个口径。
 */
export const NARROW_CONTENT_INSET_PX = 24

/**
 * 滚动条占位容差（写死并注明理由，FR-EG-007 边界情形）。
 *
 * 本机 Playwright chromium 用**覆盖式**滚动条：`offsetWidth − clientWidth == 0`，
 * 即使 `.page-scroll` 自身有竖直滚动条也是 0（`/customers` 实测）。
 * 但 CI 的 ubuntu runner 与本地 Windows 的滚动条实现**不保证一致**，
 * 经典滚动条约 15px，故取 16 向上取整。
 *
 * **它不会掩盖缺陷**：缺陷态的内容区可见宽是 **24**，与 375 差 351——比容差大一个数量级以上。
 */
export const SCROLLBAR_TOLERANCE_PX = 16

/**
 * 取整容差：几何量用 `getBoundingClientRect()` 的浮点值，实测有 `.4` / `.6` 这类半像素底边。
 * 1px 是**选定**的（`research.md` §8 第 3 条如实标注了「未逐档量过」），
 * 若实现中发现需要更大容差，**必须写明理由**，不得默默放大。
 */
export const PIXEL_TOLERANCE_PX = 1

/**
 * 活性下限：每类不变式**至少**要有这么多页真的被断言过，否则用例红。
 *
 * **这不是形式检查**：短/长页归属依赖活数据，`/customers` 在两次探针之间就从 19 行变成 20 行。
 * 若判据写成「若没溢出则断言填满」，数据一变就可能**永远不满足条件、断言从不执行**而套件全绿——
 * 本仓的 091 缺陷就在**全站内容宽 0** 的同时**全量测试全绿**（断言全落在文案上）；
 * 048 的 e2e 套件更是 22/23 用例**从未被执行过**却长期存在。
 */
export const LIVENESS = {
  /** 宽屏短页 ≥ 2（今天实测 4 页）。 */
  wideShort: 2,
  /** 其中至少要有一个走「ProTable 根」入口，一个走「Card 根」入口——缺哪个入口都会红。 */
  wideShortProTableRoot: 1,
  wideShortCardRoot: 1,
  /** 宽屏长页 ≥ 1（今天实测 2 页：`/users`、`/customers`）。 */
  wideLong: 1,
  /** 窄屏 ≥ 4（判据与数据无关，没有跳过风险，故下限就是采样页数）。 */
  narrow: 4,
} as const

/** 两阶段等待的阶段二：每 100ms 轮询一次，连续两次读数一致才算稳定。 */
const POLL_INTERVAL_MS = 100

/** 阶段二的硬超时：到点**抛错**（用例红），不存在「等到一半就继续」的静默路径。 */
const STABLE_TIMEOUT_MS = 8_000

/** 阶段一的硬超时：要素齐备等不到，同样是红。 */
const READY_TIMEOUT_MS = 8_000

/** 页面里的选择器（传进 `page.evaluate`，不在浏览器侧闭包引用模块常量）。 */
const SEL = {
  scroll: '.page-scroll',
  content: '.ant-layout-content',
  footer: '.ant-layout-footer',
  container: '.page-container',
  menuRoot: '.ant-menu-root',
  spinner: '.ant-spin-spinning',
  /**
   * 页面根卡片（ProTable 页）。`.ant-pro-table` 下有**两张** `.ant-pro-card`：
   * 搜索表单那张（`.ant-pro-table-search`）与表格那张——只撑后者，故必须 `:not()`。
   * 第二条覆盖「多包一层 div」的页面（含 4 个 `.page-stack`：customers / invoices / products / tags）。
   */
  rootCardProTable: [
    '.page-fade > .ant-pro-table > .ant-pro-card:not(.ant-pro-table-search)',
    '.page-fade > div > .ant-pro-table > .ant-pro-card:not(.ant-pro-table-search)',
  ],
  /** 页面根卡片（Card 作页根：/data-retention、/departments、/exports/scheduled）。 */
  rootCardCard: '.page-fade > .ant-card',
  /** 长页契约里用到的「末行」；`tr.ant-table-row` 排除 antd 的 `.ant-table-measure-row`。 */
  tableRow: '.ant-table-tbody > tr.ant-table-row',
} as const

/** 页面根卡片的两种入口。`fill == 0` 在**两个入口上各有样本**，缺哪个都会红。 */
export type CardKind = 'pro-table' | 'card'

export type ListPageGeometry = {
  path: string
  /** 滚动盒（= `.page-scroll` = `.ant-layout-content`，同一个人节点）底边。 */
  scrollBottom: number
  /** 滚动盒自身下内边距（宽屏 20，窄屏 12）。 */
  scrollPadBottom: number
  scrollClientHeight: number
  scrollHeight: number
  /** 页脚**实测**高度——不写死 36 / 56。 */
  footerHeight: number
  /** 内容区下内边距（与 `scrollPadBottom` 同节点，单列是为了让判据读起来与 FR-EG-006 一致）。 */
  contentPadBottom: number
  /** 页面根卡片底边；找不到时为 `null`（此时用例必须红，不得静默）。 */
  cardBottom: number | null
  /** 卡片**自身**下外边距（ProTable 页 0，Card 页 16）。 */
  cardMarginBottom: number
  cardKind: CardKind | null
  rows: number
  innerHeight: number
  docScrollWidth: number
  docInnerWidth: number
}

export type NarrowShellGeometry = {
  path: string
  /** 内容区可见宽度 = `.page-scroll` 的 `clientWidth`（含其自身左右内边距）⇒ 375。 */
  contentVisibleWidth: number
  scrollPadLeft: number
  scrollPadRight: number
  /** 内容容器宽度 = `.page-container` 的**内容盒**宽 ⇒ 351。**与上一个是两个量。** */
  contentContainerWidth: number
  menuWidth: number
  menuHeight: number
  /** 菜单容器的锚点是否认对了（见文件内 `menuAnchorReason` 的判据）。 */
  menuAnchorOk: boolean
  menuAnchorReason: string
  docOverflow: number
}

/**
 * `fill`：**短页**必须为 0（±1px）——卡片吃满内容区，余量为零。
 *
 * `滚动盒底边 − 滚动盒自身下内边距 − 卡片底边 − 卡片自身下外边距`
 *
 * 长页上它**无意义**（实测为负），故长页只断言 `fill ≤ 0`（不得缩回去），绝不断言「填满」。
 */
export function fillOf(g: ListPageGeometry): number {
  if (g.cardBottom === null) return Number.NaN
  return g.scrollBottom - g.scrollPadBottom - g.cardBottom - g.cardMarginBottom
}

/**
 * `residual`：**短页**必须为 0（±1px）——卡片下方的余量**只剩可解释的外壳占位**。
 *
 * `(视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片自身下外边距)`
 *
 * 它与 `fill` 是**两种表述**：`fill` 锚在滚动盒上，`residual` 锚在视口与**现量的**页脚上。
 * 两条都断言（FR-EG-005 与 FR-EG-006 各一条），任一条成立都不足以替代另一条。
 */
export function residualOf(g: ListPageGeometry): number {
  if (g.cardBottom === null) return Number.NaN
  return g.innerHeight - g.cardBottom - (g.footerHeight + g.contentPadBottom + g.cardMarginBottom)
}

/** 滚动盒的溢出量：`> 0` ⇒ 长页（内容撑高、由滚动盒滚动）；`<= 0` ⇒ 短页。 */
export function overflowOf(g: ListPageGeometry): number {
  return g.scrollHeight - g.scrollClientHeight
}

/** 页面级横向溢出（FR-EG-009）：恒为 0。宽屏与窄屏两侧都要断。 */
export function docOverflowOf(g: ListPageGeometry | NarrowShellGeometry): number {
  return 'docScrollWidth' in g ? g.docScrollWidth - g.docInnerWidth : g.docOverflow
}

/**
 * 两阶段等待（`research.md` §2）——**不用固定 sleep 当就绪信号**，**不用 `networkidle`**。
 *
 * ```
 * 阶段一「要素齐备」：判据依赖的要素全部在场，且无 spinner
 * 阶段二「几何自稳定」：每 100ms 重读一遍判据所需的全部输入（取整），连续两次一致才继续；
 *                     8s 超时 ⇒ 抛错 ⇒ 用例红
 * ```
 *
 * <p><b>为什么不能只等「卡片出现」</b>：实测卡片在 **67–107ms** 就出现，但几何要到约 **400–500ms** 才定下来
 * （收敛后 1.2s 复读，8 页逐页读数完全相同）。任何以「卡片在不在」为就绪信号的写法都会读到**中间态**。
 *
 * <p>阶段二等的是**判据本身依赖的那些量**，不是某个代理信号——所以它不可能「等错了东西」；
 * 且它**自证收敛**：超时就抛错。
 *
 * @param opts.requireRootCard 列表页传 `true`；窄屏的 `/stats` 是仪表盘、**没有**页面根卡片，传 `false`
 * @param opts.requireMenu     窄屏传 `true`（菜单容器本身就是判据的一部分）
 */
export async function waitForGeometryStable(
  page: Page,
  opts: { requireRootCard?: boolean; requireMenu?: boolean } = {},
): Promise<void> {
  await page.waitForFunction(
    ({ sel, requireRootCard, requireMenu }) => {
      const has = (s: string) => !!document.querySelector(s)
      if (!has(sel.scroll) || !has(sel.content) || !has(sel.footer)) return false
      if (has(sel.spinner)) return false
      if (requireMenu && !has(sel.menuRoot) && !has('.ant-menu')) return false
      if (requireRootCard && !sel.rootCardProTable.some((s) => has(s)) && !has(sel.rootCardCard)) return false
      return true
    },
    { sel: SEL, requireRootCard: !!opts.requireRootCard, requireMenu: !!opts.requireMenu },
    { timeout: READY_TIMEOUT_MS },
  )

  const deadline = Date.now() + STABLE_TIMEOUT_MS
  let prev = ''
  for (;;) {
    // 读数集合 = 判据本身依赖的那些量（取整），不是某个代理信号
    const snap = await page.evaluate((sel) => {
      const rectOf = (el: Element | null) => (el ? el.getBoundingClientRect() : null)
      const num = (v: number | undefined) =>
        typeof v === 'number' && Number.isFinite(v) ? Math.round(v) : null
      const card =
        sel.rootCardProTable.map((s) => document.querySelector(s)).find((el) => !!el) ??
        document.querySelector(sel.rootCardCard)
      const sc = document.querySelector(sel.scroll) as HTMLElement | null
      const ft = document.querySelector(sel.footer)
      const menu = document.querySelector(sel.menuRoot) ?? document.querySelector('.ant-menu')
      return {
        scrollBottom: num(rectOf(sc)?.bottom),
        scrollHeight: num(sc?.scrollHeight),
        scrollClient: num(sc?.clientHeight),
        scrollClientWidth: num(sc?.clientWidth),
        footerH: num(rectOf(ft)?.height),
        cardBottom: num(rectOf(card)?.bottom),
        docScrollW: num(document.documentElement.scrollWidth),
        innerW: num(window.innerWidth),
        menuBox: menu ? `${num(rectOf(menu.parentElement)?.width)}x${num(rectOf(menu.parentElement)?.height)}` : null,
      }
    }, SEL)

    const cur = JSON.stringify(snap)
    if (cur === prev) return
    if (Date.now() > deadline) {
      throw new Error(
        `几何在 ${STABLE_TIMEOUT_MS}ms 内未稳定（${page.url()}）：最后一次读数 ${cur}`,
      )
    }
    prev = cur
    // 这是**轮询间隔**，不是就绪信号——就绪由「连续两次读数一致」证明，超时由上面的 deadline 硬兜。
    await page.waitForTimeout(POLL_INTERVAL_MS)
  }
}

/** 量取列表页几何（FR-EG-005/006）。调用前必须先 `waitForGeometryStable(page, { requireRootCard: true })`。 */
export async function readListPageGeometry(page: Page): Promise<ListPageGeometry> {
  return page.evaluate((sel) => {
    const path = location.pathname
    const px = (v: string) => Number.parseFloat(v) || 0
    const sc = document.querySelector(sel.scroll) as HTMLElement | null
    const ft = document.querySelector(sel.footer)
    const card =
      sel.rootCardProTable.map((s) => document.querySelector(s)).find((el) => !!el) ??
      document.querySelector(sel.rootCardCard)
    const cardKind: 'pro-table' | 'card' | null = card
      ? sel.rootCardProTable.some((s) => document.querySelector(s) === card)
        ? 'pro-table'
        : 'card'
      : null
    const scRect = sc?.getBoundingClientRect()
    const scStyle = sc ? getComputedStyle(sc) : null
    const cardStyle = card ? getComputedStyle(card) : null
    return {
      path,
      scrollBottom: scRect?.bottom ?? Number.NaN,
      scrollPadBottom: scStyle ? px(scStyle.paddingBottom) : Number.NaN,
      scrollClientHeight: sc?.clientHeight ?? Number.NaN,
      scrollHeight: sc?.scrollHeight ?? Number.NaN,
      footerHeight: ft?.getBoundingClientRect().height ?? Number.NaN,
      contentPadBottom: scStyle ? px(scStyle.paddingBottom) : Number.NaN,
      cardBottom: card ? card.getBoundingClientRect().bottom : null,
      cardMarginBottom: cardStyle ? px(cardStyle.marginBottom) : Number.NaN,
      cardKind,
      rows: document.querySelectorAll(sel.tableRow).length,
      innerHeight: window.innerHeight,
      docScrollWidth: document.documentElement.scrollWidth,
      docInnerWidth: window.innerWidth,
    }
  }, SEL)
}

/**
 * 量取窄屏外壳几何（FR-EG-007/008/009）。
 *
 * <p><b>菜单容器的锚点</b>：`.ant-menu-root` 的**直接父元素**——语义是「承载菜单的那个容器」，
 * 而不是「外壳的第一个子元素」那种位置假设（两种取法实测同一节点，见 `research.md` §5）。
 * 并顺带判定「它是不是**同时承载内容区的那层外壳布局节点**的直接子元素」，让**认错对象不可能**：
 * 认错时失败信息会直接说清，而不是给出一个莫名其妙的小数值。
 */
export async function readNarrowShellGeometry(page: Page): Promise<NarrowShellGeometry> {
  return page.evaluate((sel) => {
    const px = (v: string) => Number.parseFloat(v) || 0
    const sc = document.querySelector(sel.scroll) as HTMLElement | null
    const container = document.querySelector(sel.container)
    const menuRoot = document.querySelector(sel.menuRoot) ?? document.querySelector('.ant-menu')
    const menuBox = menuRoot?.parentElement ?? null
    const scStyle = sc ? getComputedStyle(sc) : null
    const cRect = container?.getBoundingClientRect()
    const cStyle = container ? getComputedStyle(container) : null

    // 锚点判据：菜单容器必须是**外壳布局节点的直接子元素**，而那层外壳同时承载内容区。
    //
    // 实测的窄屏结构是（App.tsx）：外壳 Layout 下并排两个子节点 —— 承载菜单的普通 div、
    // 以及装着 .page-scroll 与页脚的 Layout。**菜单容器与内容区是兄弟**，
    // 所以「菜单容器包含内容区」是错的判据（第一版就是这么写错的，四页一起红在 `holdsContent=false`）。
    // 正确的表述是「外壳（= 菜单容器的父节点）同时装着这两样」，也就是 091 取证的
    // `outer.firstElementChild === .ant-menu.parentElement` 那条实测结论。
    const shell = menuBox?.parentElement ?? null
    let anchorOk = false
    let reason = '未找到菜单根元素'
    if (menuRoot && menuBox && shell) {
      const shellHoldsContent = !!sc && shell.contains(sc)
      const insideContent = !!sc && sc.contains(menuBox) // 菜单被塞进内容区里（认到嵌套菜单）
      const shellIsRoot = shell === document.body || shell === document.documentElement
      anchorOk = shellHoldsContent && !insideContent && !shellIsRoot
      reason = anchorOk
        ? `锚点正确：外壳 <${shell.tagName.toLowerCase()}> 同时承载内容区，菜单条是它的直接子元素`
        : `锚点可疑：shellHoldsContent=${shellHoldsContent} insideContent=${insideContent} shellIsRoot=${shellIsRoot}` +
          `（菜单根 <${menuRoot.tagName.toLowerCase()} class="${menuRoot.className}">` +
          `，其父 <${menuBox.tagName.toLowerCase()} class="${menuBox.className || '（无类名）'}">）`
    }

    return {
      path: location.pathname,
      contentVisibleWidth: sc?.clientWidth ?? Number.NaN,
      scrollPadLeft: scStyle ? px(scStyle.paddingLeft) : Number.NaN,
      scrollPadRight: scStyle ? px(scStyle.paddingRight) : Number.NaN,
      contentContainerWidth: cRect && cStyle
        ? cRect.width - px(cStyle.paddingLeft) - px(cStyle.paddingRight) - px(cStyle.borderLeftWidth) - px(cStyle.borderRightWidth)
        : Number.NaN,
      menuWidth: menuBox ? menuBox.getBoundingClientRect().width : Number.NaN,
      menuHeight: menuBox ? menuBox.getBoundingClientRect().height : Number.NaN,
      menuAnchorOk: anchorOk,
      menuAnchorReason: reason,
      docOverflow: document.documentElement.scrollWidth - window.innerWidth,
    }
  }, SEL)
}

/**
 * 长页契约的第三条（FR-EG-012）：**末行完整可见**（不被 `.ant-table{overflow:hidden}` 裁掉）。
 *
 * 做法：把滚动盒滚到底再量。若表格被压扁/裁切，末行就会落在滚动盒可见内容区的下边界之外——
 * 这正是 090 明确警告过的形态（「绝不能给表格设 `min-height: 0`」）。
 *
 * @returns `lastRowOverflow > 0` 表示末行被裁掉；`rows` 是实测行数（供报告与诊断分类翻转用）
 */
export async function measureLastRowReachability(
  page: Page,
): Promise<{ rows: number; lastRowOverflow: number | null }> {
  return page.evaluate((sel) => {
    const sc = document.querySelector(sel.scroll) as HTMLElement | null
    if (sc) sc.scrollTop = sc.scrollHeight
    const rows = document.querySelectorAll(sel.tableRow)
    const last = rows[rows.length - 1] as HTMLElement | undefined
    if (!sc || !last) return { rows: rows.length, lastRowOverflow: null }
    const style = getComputedStyle(sc)
    const padBottom = Number.parseFloat(style.paddingBottom) || 0
    const scRect = sc.getBoundingClientRect()
    const lastRect = last.getBoundingClientRect()
    return { rows: rows.length, lastRowOverflow: lastRect.bottom - (scRect.bottom - padBottom) }
  }, SEL)
}

const r2 = (n: number) => Math.round(n * 100) / 100
const show = (n: number | null) => (n === null ? 'null' : String(r2(Number.isNaN(n) ? Number.NaN : n)))

/** 逐页打印全部实测值（SC-EG-008）：人**不必读源码**就能核对「它到底测了什么」。 */
export function reportListPage(g: ListPageGeometry, bucket: string): void {
  console.log(
    `[092][${bucket}] ${g.path} 行数=${g.rows} 溢出=${show(overflowOf(g))} fill=${show(fillOf(g))}` +
      ` residual=${show(residualOf(g))} 卡片=${g.cardKind ?? '未找到'}` +
      `(底边 ${show(g.cardBottom)} / 下外边距 ${show(g.cardMarginBottom)})` +
      ` 滚动盒底边=${show(g.scrollBottom)} 自身下内边距=${show(g.scrollPadBottom)}` +
      ` 页脚实测高=${show(g.footerHeight)} 内容区下内边距=${show(g.contentPadBottom)}` +
      ` 视口高=${g.innerHeight} 横向溢出=${show(docOverflowOf(g))}`,
  )
}

/** 逐页打印窄屏四量（SC-EG-008）。 */
export function reportNarrowPage(g: NarrowShellGeometry): void {
  console.log(
    `[092][窄屏] ${g.path} 内容区可见宽=${show(g.contentVisibleWidth)}` +
      ` 内容容器宽=${show(g.contentContainerWidth)}` +
      ` 内容区左右内边距=${show(g.scrollPadLeft)}/${show(g.scrollPadRight)}` +
      ` 菜单=${show(g.menuWidth)}×${show(g.menuHeight)} 横向溢出=${show(g.docOverflow)}` +
      ` 锚点=${g.menuAnchorOk ? 'OK' : '可疑'}`,
  )
}

/** 打印一类不变式的样本数与下限——与紧随其后的硬断言成对出现，便于核对。 */
export function reportBucket(name: string, pages: string[], floor: number): void {
  console.log(`[092][活性] ${name}：样本 ${pages.length} 页（下限 ${floor}）→ ${pages.join(', ') || '（空）'}`)
}

/**
 * 活性**硬断言**（FR-EG-010 / FR-EG-011 / SC-EG-005）：样本数低于下限 ⇒ **红**。
 *
 * <p>⚠️ 这里**没有**「count === 0 就跳过」的分支，也**不得**加：**零样本必须是红的**。
 * 「跳过」在本仓比「假红」昂贵得多——091 的窄屏分支在全站内容宽 0 时全量绿，
 * 048 的 22/23 用例从未被执行过，都是同一件事的两种形态。
 */
export function assertLiveness(bucket: string, pages: string[], floor: number, hint: string): void {
  expect(
    pages.length,
    `【活性】${bucket} 样本数为 ${pages.length}（${pages.join(', ') || '（空）'}），低于下限 ${floor}。${hint}`,
  ).toBeGreaterThanOrEqual(floor)
}

/**
 * 相对量断言（FR-EG-013）：失败信息必须带**页面路径 + 量名 + 实测值与应达值**。
 * 用 soft 断言让**一次运行报出全部坏页**，而不是只报第一个。
 */
export function expectRelative(
  ctx: { page: string; quantity: string },
  actual: number,
  expected: number,
  tolerance: number,
  expectedText?: string,
): void {
  const want = expectedText ?? `${r2(expected)}（±${tolerance}px）`
  expect
    .soft(
      Math.abs(actual - expected),
      `[${ctx.page}] ${ctx.quantity}：实测 ${show(actual)}，应达 ${want}，实际偏差 ${show(Math.abs(actual - expected))}px`,
    )
    .toBeLessThanOrEqual(tolerance)
}

/** 上界断言（长页用）：`actual <= bound`，失败信息同样带页面与量名。 */
export function expectAtMost(
  ctx: { page: string; quantity: string },
  actual: number,
  bound: number,
  boundText: string,
): void {
  expect
    .soft(actual, `[${ctx.page}] ${ctx.quantity}：实测 ${show(actual)}，应达 ${boundText}（上界 ${show(bound)}）`)
    .toBeLessThanOrEqual(bound)
}
