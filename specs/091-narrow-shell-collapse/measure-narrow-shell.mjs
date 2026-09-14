/**
 * 091 取证脚本：窄屏（≤767px）外壳内容区是否可用、宽屏是否**零变化**。
 *
 * 复现 spec.md 的实测表。只读：打已跑着的 dev server（默认 http://localhost:5173），
 * 不写库、不改代码、不装依赖（用 frontend 里已有的 @playwright/test）。
 *
 *   cd specs/091-narrow-shell-collapse && node measure-narrow-shell.mjs
 *   # 想换地址：CRM_BASE=http://localhost:5174 node measure-narrow-shell.mjs
 *
 * 为什么几何判据要放在这里而不是单测里：jsdom **没有布局引擎**，
 * clientWidth / getBoundingClientRect 全是假值。单测只能断言**结构**
 * （见 frontend/src/App.render.test.tsx 的 091 用例）。
 *
 * 判据（取值与论证见 research.md）：
 *   窄屏 内容区可见宽 = 视口宽 − 24（内容区左右内边距各 12）⇒ 296 / 351 / 390 / 743
 *        缺陷态恒为 24，内容容器恒为 0
 *   窄屏 菜单容器 = 宽同内容区可见宽 × 高 48；缺陷态是 200×200 的方块
 *   docOverflow 必须恒为 0（修复不得引入页面级横向晃动）
 *   宽屏 侧边栏 200（折叠 64）、内容容器宽、内容区四边 —— 与改动前**逐项相同**
 *
 * 结构性锚点（**不依赖菜单形态**，故修复前后可比）：
 *   .page-scroll   → Content 本身
 *   innerLayout    → .page-scroll 的父节点（内容区所在的内层 Layout）
 *   outerLayout    → innerLayout 的父节点（菜单与之并列的那层）
 *   menuBox        → outerLayout 的第一个元素子节点（缺陷态=侧边栏，修复后=菜单条）
 */
import { createRequire } from 'node:module'

const BASE = process.env.CRM_BASE || 'http://localhost:5173'
// 本脚本在 specs/ 下，依赖住在 frontend/，故按 frontend/package.json 解析
const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const { chromium } = require('@playwright/test')

const NARROW_WIDTHS = [320, 375, 414, 767]
const NARROW_PAGES = ['/stats', '/customers', '/orders', '/roles']
const WIDE_WIDTHS = [768, 1024, 1280, 1440, 1920]
const WIDE_PAGES = ['/customers', '/stats']
// 不嵌套在外壳里的独立路由（App.tsx 中单独注册）：宽屏下更不该被本项动到
const PARITY_PAGE = '/data-vision'
const HEIGHT = 812

const measure = () => {
  const q = (s, root = document) => root.querySelector(s)
  const box = (e) => {
    if (!e) return null
    const r = e.getBoundingClientRect()
    return {
      w: Math.round(r.width),
      h: Math.round(r.height),
      l: Math.round(r.left),
      t: Math.round(r.top),
      b: Math.round(r.bottom),
    }
  }
  const scroll = q('.page-scroll')
  const container = q('.page-container')
  const innerLayout = scroll ? scroll.parentElement : null
  const outerLayout = innerLayout ? innerLayout.parentElement : null
  const menuBox = outerLayout ? outerLayout.firstElementChild : null
  const menu = q('.ant-menu')
  return {
    // 缺陷①的**命中前提**（与 App.render.test.tsx 的结构判据同一个目标）
    hasSiderClass: outerLayout ? outerLayout.classList.contains('ant-layout-has-sider') : null,
    sider: box(q('.ant-layout-sider')),
    // 缺陷②：菜单容器（缺陷态是 200×200 的方块，期望是整宽×48）
    menu: box(menuBox),
    // 菜单内部是否可横向滚动（而不是被裁成死区）
    menuScroll: menu ? `${menu.scrollWidth}/${menu.clientWidth}` : null,
    innerW: box(innerLayout) ? box(innerLayout).w : null,
    containerW: box(container) ? box(container).w : null,
    // 内容区自身可见宽 / 实际内容宽
    scroll: scroll ? `${scroll.clientWidth}/${scroll.scrollWidth}` : null,
    // 页面级横向溢出：必须恒为 0
    docOverflow: document.documentElement.scrollWidth - window.innerWidth,
  }
}

const line = (label, m) =>
  label.padEnd(20) +
  `hasSider=${String(m.hasSiderClass).padEnd(5)}` +
  ` innerW=${String(m.innerW).padStart(5)}` +
  ` contW=${String(m.containerW).padStart(5)}` +
  ` scroll=${String(m.scroll).padStart(9)}` +
  ` menu=${m.menu ? `${m.menu.w}x${m.menu.h}` : 'null'}`.padEnd(11) +
  ` menuScroll=${String(m.menuScroll).padStart(9)}` +
  ` sider=${m.sider ? `${m.sider.w}x${m.sider.h}` : 'null'}`.padEnd(12) +
  ` docOverflow=${m.docOverflow}`

const login = async (page) => {
  await page.goto(BASE + '/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByLabel('验证码', { exact: true }).fill('0000')
  await page.getByRole('button', { name: /登/ }).click()
  await page.waitForURL(/stats/, { timeout: 15000 })
}

const visit = async (page, path) => {
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(900)
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: HEIGHT } })
await login(page)

console.log(`\n=== 窄屏段（内容区可见宽期望 = 视口 − 24；菜单容器期望 整宽×48）===`)
console.log(`   缺陷态读数：hasSider=True innerW=0 contW=0 scroll=24/141~633 menu=200x200`)
for (const w of NARROW_WIDTHS) {
  await page.setViewportSize({ width: w, height: HEIGHT })
  for (const path of NARROW_PAGES) {
    await visit(page, path)
    console.log(line(`${w} ${path}`, await page.evaluate(measure)))
  }
}

console.log(`\n=== 窄屏边界（FR-W-005：767 走窄屏、768 走宽屏）===`)
for (const w of [767, 768]) {
  await page.setViewportSize({ width: w, height: HEIGHT })
  await visit(page, '/customers')
  console.log(line(`${w} /customers`, await page.evaluate(measure)))
}

console.log(`\n=== 宽屏段（与改动前逐项相同；侧边栏期望 200 / 折叠 64）===`)
for (const w of WIDE_WIDTHS) {
  await page.setViewportSize({ width: w, height: HEIGHT })
  for (const path of WIDE_PAGES) {
    await visit(page, path)
    console.log(line(`${w} ${path}`, await page.evaluate(measure)))
  }
}

console.log(`\n=== parity：不嵌套外壳的独立路由（宽屏，不该被本项动到）===`)
await page.setViewportSize({ width: 1440, height: HEIGHT })
await visit(page, PARITY_PAGE)
console.log(line(`1440 ${PARITY_PAGE}`, await page.evaluate(measure)))

console.log(`\n=== 跨断点切换（宽→窄→宽，research.md §7 第 1 条）===`)
await page.setViewportSize({ width: 1024, height: HEIGHT })
await visit(page, '/customers')
const before = await page.evaluate(measure)
console.log(line('1024 初始', before))
for (const w of [375, 1024, 375, 1024]) {
  await page.setViewportSize({ width: w, height: HEIGHT })
  await page.waitForTimeout(600)
  console.log(line(`切到 ${w}`, await page.evaluate(measure)))
}
const after = await page.evaluate(measure)
const same =
  before.innerW === after.innerW &&
  before.containerW === after.containerW &&
  before.scroll === after.scroll &&
  before.sider?.w === after.sider?.w
console.log(`  回宽屏与初始逐项相同：${same ? '是' : '否'}（不残留窄屏的内联样式）`)

console.log(`\n=== 挂载即窄屏（FR-W-010：新开上下文，渲染前窗口就已经是窄屏）===`)
const narrowCtx = await browser.newContext({ viewport: { width: 375, height: HEIGHT } })
const narrowPage = await narrowCtx.newPage()
await login(narrowPage)
for (const path of ['/customers', '/orders']) {
  await visit(narrowPage, path)
  console.log(line(`375 ${path}`, await narrowPage.evaluate(measure)))
}
await narrowCtx.close()

await browser.close()
