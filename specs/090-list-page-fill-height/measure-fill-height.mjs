/**
 * 090 取证脚本：列表页「表格卡片是否吃满内容区剩余高度」。
 *
 * 复现 spec.md 的实测表。只读：打已跑着的 dev server（默认 http://localhost:5173），
 * 不写库、不改代码、不装依赖（用 frontend 里已有的 @playwright/test）。
 *
 *   cd specs/090-list-page-fill-height && node measure-fill-height.mjs
 *   # 想换地址：CRM_BASE=http://localhost:5174 node measure-fill-height.mjs
 *
 * 判据（1280×1100 视口）：
 *   gap  = 内容最低点到视口底部的距离，理论下限 56 = Content 下内边距 20 + 页脚 ≈36
 *          （Card 作页根的页面为 72，多出卡片自带的 16px 外边距）
 *   cardBot 应等于内容盒底（1100 − 36 页脚 − 20 下内边距 = 1044）
 *   pagerTop 应落在卡片底部附近（实测 1004）
 *   tblOverflow > 0 ⇒ 表格被 `.ant-table{overflow:hidden}` 裁切了，属回归
 *   hiddenDisp 必须是 "none"：Tabs 页未选中的页签不许被显示出来
 */
import { createRequire } from 'node:module'

const BASE = process.env.CRM_BASE || 'http://localhost:5173'
// 本脚本在 specs/ 下，依赖住在 frontend/，故按 frontend/package.json 解析
const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const { chromium } = require('@playwright/test')

// 入口 A 直挂 / 入口 A 包一层 div / 入口 B Card 作页根
const LIST_PAGES = [
  '/quotas',
  '/orders',
  '/customers',
  '/roles',
  '/users',
  '/data-retention',
  '/exports/scheduled',
  '/departments',
]
// 入口 C：表格在页签里（每页都量页签 1 与页签 2）
const TAB_PAGES = ['/tags', '/marketing/email', '/open-platform']
// parity：这些页面的几何不该被本项改动动到
const PARITY_PAGES = ['/stats', '/account/password', '/personal-center', '/quotas/create']

const measure = () => {
  const box = (e) => (e ? e.getBoundingClientRect() : null)
  const q = (s, root = document) => root.querySelector(s)
  const scroll = q('.page-scroll')
  const fade = q('.page-fade')
  let lastBottom = 0
  if (fade) {
    for (const el of fade.querySelectorAll('*')) {
      const r = el.getBoundingClientRect()
      if (r.height > 0 && r.bottom > lastBottom) lastBottom = r.bottom
    }
  }
  // Tabs 页：只量当前激活的页签，否则会量到隐藏页签（高度 0），结论失真
  const scope = q('.ant-tabs-tabpane-active') || document
  const sq = (s) => q(s, scope)
  const card = sq('.ant-pro-card:not(.ant-pro-table-search)')
  const pager = sq('.ant-pagination')
  const tc = sq('.ant-table-content')
  const search = sq('.ant-pro-table-search')
  return {
    gap: Math.round(window.innerHeight - lastBottom),
    scroll: `${scroll.scrollHeight}/${scroll.clientHeight}`,
    searchH: search ? Math.round(box(search).height) : null,
    rows: scope.querySelectorAll('.ant-table-tbody tr.ant-table-row').length,
    cardBot: box(card) ? Math.round(box(card).bottom) : null,
    pagerTop: box(pager) ? Math.round(box(pager).top) : null,
    tblOverflow: tc ? tc.scrollHeight - tc.clientHeight : null,
    hiddenDisp: [...document.querySelectorAll('.ant-tabs-tabpane-hidden')].map(
      (p) => getComputedStyle(p).display,
    ),
    fade: box(fade) ? [Math.round(box(fade).top), Math.round(box(fade).bottom)] : null,
  }
}

const line = (label, m) =>
  label.padEnd(24) +
  `gap=${String(m.gap).padStart(5)}  cardBot=${String(m.cardBot).padStart(5)}  pagerTop=${String(m.pagerTop).padStart(5)}` +
  `  rows=${String(m.rows).padStart(3)}  tblOverflow=${String(m.tblOverflow).padStart(3)}` +
  `  searchH=${String(m.searchH).padStart(4)}  scroll=${m.scroll}  fade=${JSON.stringify(m.fade)}` +
  (m.hiddenDisp.length ? `  hidden=${JSON.stringify(m.hiddenDisp)}` : '')

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 1100 } })
await page.goto(BASE + '/login')
await page.getByLabel('用户名').fill('admin')
await page.getByLabel('密码').fill('admin123')
await page.getByLabel('验证码', { exact: true }).fill('0000')
await page.getByRole('button', { name: /登/ }).click()
await page.waitForURL(/stats/, { timeout: 15000 })

console.log(`\n=== 列表页（视口 1280×1100，期望 gap=56 / cardBot=1044）===`)
for (const path of LIST_PAGES) {
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(900)
  console.log(line(path, await page.evaluate(measure)))
}

console.log(`\n=== 入口 C：表格在页签里（两个页签都要满）===`)
for (const path of TAB_PAGES) {
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(900)
  console.log(line(path + ' [页签1]', await page.evaluate(measure)))
  const tabs = page.locator('.ant-tabs-tab')
  if ((await tabs.count()) > 1) {
    await tabs.nth(1).click()
    await page.waitForTimeout(1200)
    console.log(line(path + ' [页签2]', await page.evaluate(measure)))
  }
}

console.log(`\n=== parity：这些页面的几何不该变（仅 Fade 区间与 gap 可读）===`)
for (const path of PARITY_PAGES) {
  await page.goto(BASE + path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(900)
  console.log(line(path, await page.evaluate(measure)))
}

await browser.close()
