import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { menuKeyOf } from '../src/constants/menuKeys'
import { login } from './helpers/login'

/**
 * 084 T021 的机械化：**侧边栏可见集合必须等于该角色的菜单授权集合**（FR-N01–N07 / SC-N01/N02）。
 *
 * <p><b>为什么它是「三侧」而不是「两侧」</b>：
 *
 * | 侧 | 取法 | 它在断言里的角色 |
 * |---|---|---|
 * | 授权 | `POST /api/v1/auth/login` 的 `data.user.menus` | 右侧：服务端认为该角色被授了什么 |
 * | 渲染 | 真实浏览器里 antd 侧边栏的 DOM | 左侧：用户实际看得见什么 |
 * | 权威树 | `GET /api/v1/roles/menu-tree`（`RoleConstants.MENU_TREE` 的序列化） | 分组/成员/名称的归组判据 |
 *
 * 三者都不读仓库文件，故这套用例在 CI 里可复现——这是它与一次性脚本的关键差别。
 * 夹具用户（每个预置角色一个）也由本文件自建，**幂等**：已存在时建号失败被显式忽略，登录仍照跑。
 *
 * <p><b>为什么必须有这道用例</b>：这不是「多一层保险」。改造前的故障形态是
 * `App.tsx` 里 `isAdmin ? adminRoutes : []` 的**整组硬门**——`role_menu` 授了、角色配置页勾得上、
 * 侧边栏永远不渲染，共 16 处 (角色,键) 对。那种缺陷对「有没有权限」的既有用例**完全不可见**，
 * 因为它不在权限链上，只在渲染链上。断言「可见 == 授权」是唯一能抓住它的形态。
 *
 * <p><b>反向验证</b>：把 `resolveVisibleMenuKeys` 临时改成复刻硬门（删掉两个键）→ 本文件的
 * 「ANALYST」用例变红并报出 `missing=custom-objects,settings/custom-fields`，同时「整组因空不渲染」
 * 使组集合断言一并变红；还原后复绿。做法与结论记在 `specs/084-menu-ia-authorization/verification.md`。
 */

/** 夹具用户的统一口令（只为验收用；建号走真实 `POST /api/v1/users`）。 */
const FIXTURE_PASSWORD = 'Test12345'

/** V75 建出的 10 个预置角色（SC-N08 枚举的那批）。 */
const PRESET_ROLES: [string, string][] = [
  ['ANALYST', 'e2e_analyst'],
  ['FINANCE_ACCOUNTANT', 'e2e_finacct'],
  ['FINANCE_MANAGER', 'e2e_finmgr'],
  ['MARKETING_MANAGER', 'e2e_mktmgr'],
  ['MARKETING_SPECIALIST', 'e2e_mktspec'],
  ['SALES_MANAGER', 'e2e_salesmgr'],
  ['SALES_REP', 'e2e_salesrep'],
  ['SUPPORT_AGENT', 'e2e_supportagent'],
  ['SUPPORT_MANAGER', 'e2e_supportmgr'],
  ['VIEWER', 'e2e_viewer'],
]

/** D2 另要求的两个**回归项**：`SALES`（既有可见集合未变）与 `ADMIN`（全量兜底，等价 D3）。 */
const SALES_FIXTURE: [string, string] = ['SALES', 'e2e_sales']

type MenuNode = { title: string; children: { key: string; title: string }[] }
type LoginBody = { data: { accessToken: string; user: { menus: string[] } } }

/** 走 dev server 的 `/api` 代理（与浏览器同一路径），故不硬编码后端端口。 */
async function apiLogin(request: APIRequestContext, username: string, password: string) {
  const res = await request.post('/api/v1/auth/login', {
    data: { username, password, captcha: '0000' },
  })
  expect(res.ok(), `${username} 登录失败：HTTP ${res.status()}`).toBeTruthy()
  return (await res.json()) as LoginBody
}

/** 建夹具用户。已存在（二次运行）时记一条日志继续——不吞掉**其他**失败：只忽略建号这一步的结果。 */
async function ensureFixtureUsers(request: APIRequestContext, token: string) {
  for (const [role, username] of [...PRESET_ROLES, SALES_FIXTURE]) {
    const res = await request.post('/api/v1/users', {
      headers: { Authorization: `Bearer ${token}` },
      data: { username, displayName: `${role} 验收用户`, role, password: FIXTURE_PASSWORD },
    })
    if (!res.ok()) console.log(`[夹具] ${username} 建号返回 HTTP ${res.status()}（二次运行属预期，继续）`)
  }
}

/** 展开全部一级分组。antd 的 inline 子菜单在收起时不渲染子项，不展开就读不到组内容。 */
async function expandAllGroups(page: Page) {
  const closed = page.locator(
    'aside.ant-layout-sider li.ant-menu-submenu:not(.ant-menu-submenu-open) > .ant-menu-submenu-title',
  )
  for (let pass = 0; pass < 4; pass++) {
    const before = await closed.count()
    if (!before) return
    // 每点一次，该元素就离开这个选择器，故每次都点「当前第一个」直到集合为空
    for (let i = 0; i < before; i++) await closed.nth(0).click()
    await page.waitForTimeout(150)
    if ((await closed.count()) >= before) return // 点不动了，交给断言去报错，不在这里死循环
  }
}

type Sidebar = { top: { key: string; label: string }[]; groups: { title: string; items: { key: string; label: string }[] }[] }

/** 读侧边栏 DOM。`data-menu-id` 形如 `rc-menu-uuid-<uuid>-<序号>-<route path|g-组键>`，取末段即路由/组键。 */
async function readSidebar(page: Page): Promise<Sidebar> {
  return page.evaluate(() => {
    const keyOf = (el: Element) => (el.getAttribute('data-menu-id') ?? '').replace(/^rc-menu-uuid-.*?-\d+-/, '')
    const root = document.querySelector('aside.ant-layout-sider ul.ant-menu-root')
    const top: { key: string; label: string }[] = []
    const groups: { title: string; items: { key: string; label: string }[] }[] = []
    if (!root) return { top, groups }
    for (const li of [...root.children]) {
      if (li.tagName !== 'LI') continue
      if (li.classList.contains('ant-menu-submenu')) {
        const titleEl = li.querySelector(':scope > .ant-menu-submenu-title')
        const subUl = li.querySelector(':scope > ul.ant-menu-sub')
        const items = subUl
          ? [...subUl.children]
              .filter((c) => c.tagName === 'LI')
              .map((c) => ({ key: keyOf(c), label: (c as HTMLElement).innerText.trim() }))
          : []
        groups.push({ title: (titleEl as HTMLElement | null)?.innerText.trim() ?? '', items })
      } else {
        top.push({ key: keyOf(li), label: (li as HTMLElement).innerText.trim() })
      }
    }
    return { top, groups }
  })
}

/** 侧边栏里渲染出的全部菜单键（置顶项 + 各组子项），即「用户实际看得见的集合」。 */
const keysOf = (s: Sidebar) =>
  new Set([
    ...s.top.map((i) => menuKeyOf(i.key)),
    ...s.groups.flatMap((g) => g.items.map((i) => menuKeyOf(i.key))),
  ])

/** 登录 → 展开 → 读侧边栏，返回「渲染出的菜单键集合」与原始结构。 */
async function sidebarOf(page: Page, username: string): Promise<{ keys: Set<string>; sidebar: Sidebar }> {
  await login(page, username, FIXTURE_PASSWORD)
  await page.waitForSelector('aside.ant-layout-sider ul.ant-menu-root')
  await expandAllGroups(page)
  const sidebar = await readSidebar(page)
  return { keys: keysOf(sidebar), sidebar }
}

const sorted = (s: Iterable<string>) => [...s].sort()

/** 全部夹具角色的授权集合 + 权威树。`beforeAll` 里取一次，供各用例比对。 */
let granted: Record<string, string[]> = {}
let groups: MenuNode[] = []
let allKeys: string[] = []

test.beforeAll(async ({ request }) => {
  const admin = await apiLogin(request, 'admin', 'admin123')
  await ensureFixtureUsers(request, admin.data.accessToken)

  granted = { ADMIN: [...admin.data.user.menus].sort() }
  for (const [role, username] of [...PRESET_ROLES, SALES_FIXTURE]) {
    const body = await apiLogin(request, username, FIXTURE_PASSWORD)
    granted[role] = [...body.data.user.menus].sort()
  }

  const tree = await request.get('/api/v1/roles/menu-tree', {
    headers: { Authorization: `Bearer ${admin.data.accessToken}` },
  })
  expect(tree.ok()).toBeTruthy()
  groups = ((await tree.json()) as { data: MenuNode[] }).data
  allKeys = groups.flatMap((g) => g.children.map((i) => i.key))
})

test.describe('菜单可见性：可见 == 授权（084 FR-N01–N07 / SC-N01/N02）', () => {
  // 每条用例要登录 + 展开 10 个分组 + 读 DOM，比默认 30s 紧，故显式放宽到 60s
  test.describe.configure({ timeout: 60_000 })

  for (const [role, username] of PRESET_ROLES) {
    test(`${role}：侧边栏可见集合 == 授权集合`, async ({ page }) => {
      const { keys, sidebar } = await sidebarOf(page, username)

      // A：集合相等（多一个＝「没勾却出现」；少一个＝「勾了看不到」）
      expect(sorted(keys)).toEqual(granted[role])

      // 「首页」是置顶项、按既有行为不参与可见性过滤，故它必须始终在，且是唯一的置顶项
      expect(keys.has('stats')).toBe(true)
      expect(sidebar.top.map((i) => menuKeyOf(i.key))).toEqual(['stats'])

      // B/C/D：归组。每组渲染出的成员必须恰是「授权 ∩ 该组在权威处的成员」，
      // 空组不渲染（否则会渲染出一个点不进去的空分组），组名必须是权威处的组名
      const authorityGroupOf = new Map(groups.flatMap((g) => g.children.map((i) => [i.key, g.title] as const)))
      for (const group of sidebar.groups) {
        expect(groups.map((g) => g.title)).toContain(group.title)
        const members = groups.find((g) => g.title === group.title)?.children.map((i) => i.key) ?? []
        const rendered = group.items.map((i) => menuKeyOf(i.key))
        for (const k of rendered) expect(authorityGroupOf.get(k)).toBe(group.title)
        expect(sorted(new Set(rendered))).toEqual(sorted(members.filter((k) => granted[role].includes(k))))
      }
      const expectTitles = groups
        .filter((g) => g.title !== '首页' && g.children.some((i) => granted[role].includes(i.key)))
        .map((g) => g.title)
      expect(sidebar.groups.map((g) => g.title)).toEqual(expectTitles)
    })
  }

  test('SALES（既有角色回归项）：可见集合未变（22 项，改造前后一致）', async ({ page }) => {
    const { keys } = await sidebarOf(page, SALES_FIXTURE[1])
    expect(sorted(keys)).toEqual(granted.SALES)
  })

  test('ADMIN：全量兜底——撤掉 UI 硬门后仍看到全部菜单（D3）', async ({ page }) => {
    await login(page, 'admin', 'admin123')
    await page.waitForSelector('aside.ant-layout-sider ul.ant-menu-root')
    await expandAllGroups(page)
    expect(sorted(keysOf(await readSidebar(page)))).toEqual(sorted(allKeys))
  })

  test('ANALYST：已授权的「自定义对象」打得开，不是 403、不是空白页（D1-3）', async ({ page }) => {
    await login(page, 'e2e_analyst', FIXTURE_PASSWORD)
    // 用 waitForResponse 而不是事后读一个数组：请求若压根没发出，这里会以超时失败，而不是「空数组 == [200]」这种误导性报错
    const [definition] = await Promise.all([
      page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/custom-objects'),
      page.goto('/custom-objects'),
    ])
    expect(definition.status()).toBe(200)
    // 结构齐备 = 「正常加载」；「暂无数据」是测试库的真实空集，不是加载失败
    await expect(page.getByRole('columnheader', { name: '编码' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建对象' })).toBeVisible()
  })

  test('ANALYST：未授权的「角色管理」直接输 URL 仍被服务端拒绝（D1-4）', async ({ page }) => {
    await login(page, 'e2e_analyst', FIXTURE_PASSWORD)
    const statuses: number[] = []
    page.on('response', (r) => {
      if (new URL(r.url()).pathname.startsWith('/api/v1/roles')) statuses.push(r.status())
    })
    // 隐藏菜单项不是访问控制：服务端必须照旧拦（FR-N05）
    const [list] = await Promise.all([
      page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/roles'),
      page.goto('/roles'),
    ])
    expect(list.status()).toBe(403)
    // 该页面顺带打的其他 /roles* 端点（menu-tree、permission-defs）同样必须全被拒。
    // 先断言非空：否则「一个都没观察到」会让下面那条过滤断言空转通过
    expect(statuses.length).toBeGreaterThan(0)
    expect(statuses.filter((s) => s !== 403)).toEqual([])
  })
})
