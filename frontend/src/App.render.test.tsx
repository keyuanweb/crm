import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from './test/renderWithProviders'
import { fetchMe, logout } from './services/authService'
import App from './App'

// 当前登录用户。用 vi.hoisted 是因为 vi.mock 的工厂会被提升到文件顶部执行，
// 直接引用模块作用域的变量会「初始化前访问」而报错。
const { me } = vi.hoisted(() => ({
  me: {
    id: 1,
    username: 'admin',
    displayName: '系统管理员',
    role: 'ADMIN' as string,
    menus: undefined as string[] | undefined,
  },
}))

// 084 顺带修的一处旧缺陷：这三条路径原先写成 `'../../services/xxx'`，而从 `src/` 出发
// `'../../services/'` 指向的是仓库外的 `E:\code\crm\services\`（不存在）。mock 因此静默失效，
// 测试却照样绿——它执行的已不是它声称的场景（fetchMe 打真网络、用户始终未登录、侧边栏一个分组
// 都没有）。路径改对之后，下面那条「fetchMe 真的被调用」的断言才有意义。
vi.mock('./services/authService', () => ({
  fetchMe: vi.fn(async () => me),
  logout: vi.fn(),
  login: vi.fn(),
}))
vi.mock('./services/leadService', () => ({
  fetchLeads: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createLead: vi.fn(),
  updateLead: vi.fn(),
  deleteLead: vi.fn(),
  claimLead: vi.fn(),
  assignLead: vi.fn(),
}))
vi.mock('./services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(async () => []),
  fetchCustomFields: vi.fn(),
  createCustomField: vi.fn(),
  updateCustomField: vi.fn(),
  deleteCustomField: vi.fn(),
}))
// 顶栏下拉的「个人中心」会挂载 PersonalCenterPage，它在挂载时即请求个人信息；
// 不 mock 就是 jsdom 里的真实请求（与上面通知中心同一条理由）。
vi.mock('./services/personalService', () => ({
  fetchPersonalInfo: vi.fn(async () => ({
    id: 1,
    username: 'admin',
    displayName: '系统管理员',
    role: 'ADMIN',
  })),
  updateDisplayName: vi.fn(),
}))
// 顶栏下拉的「使用地图」会挂载 UsageMapPage，它在挂载后异步 `new Graph(...)` 起 AntV G6。
// jsdom 无 canvas：`getContext` 返回 null，g-canvas 随后对 null 调 clearRect，抛出**测试结束后才落地**的
// 未处理拒绝（vitest 计入 unhandled errors → 整轮退出码 1，即使 79 个用例全绿）。本文件断言的是
// Shell 的跳转，不是图渲染，故与 UsageMapPage.test.tsx 同一手法打桩 G6。
const { graphMock } = vi.hoisted(() => ({
  graphMock: vi.fn(() => {
    const chain = {
      render: vi.fn(async () => {}),
      destroy: vi.fn(),
      on: vi.fn(),
      hasNode: vi.fn(() => true),
      updateNodeData: vi.fn(),
      getData: vi.fn(() => ({ nodes: [], edges: [] })),
      translateBy: vi.fn(),
    }
    return chain
  }),
}))
vi.mock('@antv/g6', () => ({ Graph: graphMock }))

// 顶栏通知中心会发起真实请求，测试中 mock 避免 jsdom XHR 报错
vi.mock('./services/notificationService', () => ({
  fetchUnreadCount: vi.fn(async () => 0),
  fetchNotifications: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
}))

/**
 * 侧边栏容器。
 *
 * <p>可见性断言一律在它**内部**做：菜单文案与页面正文会重名（「客户管理」既是一个分组名，
 * 也可能是列表页的标题），在全文档范围查询会撞上正文里的同名文字。
 */
const sider = () => document.querySelector('.ant-layout-sider') as HTMLElement | null

/**
 * 等侧边栏挂载完成，返回限定在它内部的查询器。
 *
 * <p><b>为什么必须等、而且等待要给足</b>：`App` 的 `<Suspense>` 边界包着整个 `Routes`，
 * 懒加载的页面 chunk 在 vitest 里首次 transform 要 **2 秒以上**；这段期间整棵已挂载的树被 React
 * 置为 `display:none`，侧边栏查不到。默认 1 秒的等待会在这里假失败（实测 2313ms）。
 */
const SHELL_TIMEOUT = 20000

async function nav() {
  await waitFor(() => expect(sider()).not.toBeNull(), { timeout: SHELL_TIMEOUT })
  return within(sider() as HTMLElement)
}

/**
 * 外壳里唯一的那处 `<Menu>`（顶部下拉与全局搜索都不渲染 Menu）。
 *
 * <p>可见性断言同样必须**限定在菜单内部**：菜单文案与页面正文会重名（「客户管理」既是分组名，
 * 也可能是列表页标题），在全文档范围查询会撞上正文里的同名文字。
 *
 * <p><b>091 起窄屏不再渲染 `.ant-layout-sider`</b>（改用普通容器承载同一份菜单，见 App.tsx
 * 的 isMobile 分支），因此**窄屏形态不能用 `nav()`** —— 它等的是侧边栏，窄屏下永远等不到；
 * 即便先按宽屏拿到引用再 resize，那个引用也会指向一棵已被卸载的子树。菜单本身的定位
 * 对宽窄两种形态都成立，故窄屏形态一律用下面这个。
 */
const shellMenu = () => document.querySelector('.ant-menu') as HTMLElement | null

/** 等菜单挂载完成，返回限定在它内部的查询器（宽窄两种形态通用）。 */
async function navMenu() {
  await waitFor(() => expect(shellMenu()).not.toBeNull(), { timeout: SHELL_TIMEOUT })
  return within(shellMenu() as HTMLElement)
}

/**
 * 每次调用都**重新定位**当前菜单节点，返回限定在它内部的查询器。
 *
 * <p><b>为什么不能复用 `navMenu()` 的返回值做跨形态断言</b>：`within()` 绑定的是**节点快照**。
 * 宽↔窄切换会把整个菜单容器换掉（侧边栏卸载、横条挂载），旧引用此后指向一棵已被卸载的
 * 子树 —— 它的内容永远停在切换前那一刻，断言会以「文案还在」的形式假红。
 * 凡是要跨越形态切换的断言，一律用这个。
 */
const menuNow = () => within(shellMenu() as HTMLElement)

describe('App 全量渲染冒烟（/leads 白屏/错误页回归）', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('accessToken', 'test-token')
    me.role = 'ADMIN'
    me.menus = undefined
  })

  it(
    '访问 /leads 时 App（Shell+菜单+页面）同步渲染不抛异常',
    async () => {
      expect(() => renderWithProviders(<App />, { route: '/leads' })).not.toThrow()

      // 只断言「不抛异常」的话，白屏、以及「用户没加载出来、侧边栏空着」同样算通过。
      // 下面两条把「渲染真的发生了」钉住：登录接口被调过 + 管理员能看到分组标题。
      const menu = await nav()
      await waitFor(() => expect(fetchMe).toHaveBeenCalled(), {
        timeout: SHELL_TIMEOUT,
      })
      expect(await menu.findByText('客户管理', {}, { timeout: SHELL_TIMEOUT })).toBeTruthy()
      // 管理员：11 个分组标题都在（全量兜底 FR-N04）
      for (const title of ['系统管理', '流程配置', '审计维护']) {
        expect(menu.getByText(title)).toBeTruthy()
      }
    },
    SHELL_TIMEOUT * 2,
  )

  it(
    '084 边界情况②：空分组不渲染，有可见项的分组才渲染（FR-N02）',
    async () => {
      // ANALYST 的 7 项真实授权（verification.md §T002）：approvals / custom-objects /
      // data-vision / exports / reports / settings/custom-fields / stats
      me.role = 'ANALYST'
      me.menus = [
        'approvals',
        'custom-objects',
        'data-vision',
        'exports',
        'reports',
        'settings/custom-fields',
        'stats',
      ]
      renderWithProviders(<App />, { route: '/leads' })

      // 正向对照：先确认「有可见项的分组渲染了」——否则整个侧边栏都没渲染时，
      // 下面那批「不该出现」的断言会全部通过。
      const menu = await nav()
      await menu.findByText('工作台', {}, { timeout: SHELL_TIMEOUT }) // approvals
      expect(menu.getByText('数据分析')).toBeTruthy() // reports / exports / data-vision
      expect(menu.getByText('流程配置')).toBeTruthy() // settings/custom-fields / custom-objects
      expect(menu.getByText('首页')).toBeTruthy() // 置顶项不过滤（FR-N04 相关）

      // 一个可见项都没有的分组不得留下空的分组头
      for (const empty of [
        '客户管理',
        '销售管理',
        '交易管理',
        '营销管理',
        '客户服务',
        '系统管理',
        '审计维护',
      ]) {
        expect(menu.queryByText(empty)).toBeNull()
      }
    },
    SHELL_TIMEOUT * 2,
  )
})

/**
 * 084：Shell 的交互路径与窄屏形态。
 *
 * <p>App.tsx 的菜单构建被 084 重写后，下面这些路径**一条用例都没有**：窄屏拍平（`flattenMenuItems`）、
 * `resize` 监听、顶栏下拉的三个跳转项、两种语言切换、折叠按钮、退出登录。它们不是新功能，但
 * 084 的重写使全局函数覆盖率从 083 交付时的 22.88% 降到 20.19%（阈值 21.4，`vite.config.ts`），
 * 而这些恰恰是重写所触及文件里未被调用的那些函数。补齐既是恢复门禁，也是把用户真会走的路径钉住。
 */
describe('084：Shell 交互路径与窄屏形态', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('accessToken', 'test-token')
    me.role = 'ADMIN'
    me.menus = undefined
    vi.mocked(logout).mockClear()
  })

  it(
    '窄屏（<768px）把分组拍平成一级项，resize 事件触发形态切换',
    async () => {
      const width = window.innerWidth
      renderWithProviders(<App />, { route: '/leads' })
      // 091 订正：这里原先用 nav()（把范围限定在侧边栏内）。窄屏自 091 起**不再渲染侧边栏**
      // （改用普通容器承载同一份菜单），于是那个引用在 resize 之后指向一棵已被卸载的子树，
      // 断言落空、用例假红。范围改锚在菜单本身 —— 对宽窄两种形态都成立，
      // 且仍然避开了正文里的同名文字（页面正文也会出现「客户管理」）。
      const menu = await navMenu()
      // 宽屏：分组以可折叠 submenu 呈现（分组标题可见）
      expect(menu.getByText('客户管理')).toBeTruthy()

      try {
        window.innerWidth = 375
        fireEvent(window, new Event('resize'))
        // 窄屏不支持分组：分组标题整体消失……（跨形态断言必须重新定位，见 menuNow 的注释）
        await waitFor(() => expect(menuNow().queryByText('客户管理')).toBeNull(), {
          timeout: SHELL_TIMEOUT,
        })
        // ……组内的项升为一级项，仍然可见（「拍平」而不是「隐藏」）
        expect(await menuNow().findByText('线索', {}, { timeout: SHELL_TIMEOUT })).toBeTruthy()
      } finally {
        window.innerWidth = width
      }
    },
    SHELL_TIMEOUT * 2,
  )

  it(
    '顶栏用户菜单：三个跳转项、两种语言、退出登录都能走通',
    async () => {
      localStorage.setItem('refreshToken', 'refresh-1')
      renderWithProviders(<App />, { route: '/leads' })
      await nav()

      // 下拉默认 hover 触发；用头像定位它的触发器（页面上不止一个 dropdown）
      const openUserMenu = () => {
        const trigger = document.querySelector('.ant-avatar')?.closest('.ant-dropdown-trigger')
        expect(trigger).toBeTruthy()
        fireEvent.mouseEnter(trigger as HTMLElement)
      }

      // 三项跳转各自落到自己的页面（断言落点页自己的元素出现，即「真的跳过去了」）
      openUserMenu()
      fireEvent.click(await screen.findByText('app.personalCenter'))
      expect(
        (await screen.findAllByTestId('basic-info-card', {}, { timeout: SHELL_TIMEOUT })).length,
      ).toBeGreaterThan(0)

      openUserMenu()
      fireEvent.click(await screen.findByText('app.usageMap'))
      // 用正则：标题元素里除文案外还有表情前缀，文本被拆成两个节点，精确匹配取不到
      expect(
        await screen.findByText(/pages\.usageMap\.pageTitle/, {}, { timeout: SHELL_TIMEOUT }),
      ).toBeTruthy()

      openUserMenu()
      fireEvent.click(await screen.findByText('app.changePassword'))
      expect(
        await screen.findByText('pages.changePassword.title', {}, { timeout: SHELL_TIMEOUT }),
      ).toBeTruthy()

      // 语言：切到 English 再切回中文，并以 localStorage 的持久化结果为证；
      // 结束时恢复中文，避免污染同文件其他用例。
      //
      // 098 订正（原文写的是「文案是字面量，不受 i18n 是否加载影响」，本行起已不成立）：
      // 两个语言项由裸字面量 `'中文'` / `'English'` 改走**既有键** `app.zh` / `app.en`，
      // 故这里改断**键名**（`src/test/setup.ts` 的 i18n mock 让 `t(key)` 原样返回 key）。
      // 注意：这两键在 zh-CN 与 en 里**同值**（语言自称/endonym，故意不译），
      // 所以**切到 en 之后**它照样显示「中文」——用例改为断键名不受此影响，断言的有效性不变。
      openUserMenu()
      fireEvent.click(screen.getAllByText('app.en')[0])
      await waitFor(() => expect(localStorage.getItem('app_lang')).toBe('en'))
      openUserMenu()
      fireEvent.click(screen.getAllByText('app.zh')[0])
      await waitFor(() => expect(localStorage.getItem('app_lang')).toBe('zh-CN'))

      // 退出登录：带 refreshToken 时调登出接口，并跳走（Shell 消失 = 已离开登录态页面）
      openUserMenu()
      fireEvent.click(screen.getByText('app.logout'))
      await waitFor(() => expect(logout).toHaveBeenCalledWith('refresh-1'))
      await waitFor(() => expect(sider()).toBeNull(), { timeout: SHELL_TIMEOUT })
    },
    SHELL_TIMEOUT * 2,
  )

  it('顶栏折叠按钮切换侧边栏折叠态', async () => {
    renderWithProviders(<App />, { route: '/leads' })
    await nav()
    expect(sider()?.className).not.toContain('ant-layout-sider-collapsed')

    // 098：折叠按钮的 aria-label 已改走 i18n 键 `app.toggleMenu`；此处断言的是**键名**
    // （`src/test/setup.ts` 的 i18n mock 让 `t(key)` 原样返回 key，缺键会直接抛错）。
    fireEvent.click(screen.getByLabelText('app.toggleMenu'))
    await waitFor(() => expect(sider()?.className).toContain('ant-layout-sider-collapsed'))

    // 098：折叠按钮的 aria-label 已改走 i18n 键 `app.toggleMenu`；此处断言的是**键名**
    // （`src/test/setup.ts` 的 i18n mock 让 `t(key)` 原样返回 key，缺键会直接抛错）。
    fireEvent.click(screen.getByLabelText('app.toggleMenu'))
    await waitFor(() => expect(sider()?.className).not.toContain('ant-layout-sider-collapsed'))
  })
})

/**
 * 被组件库识别为「含侧边栏的布局」的那个容器。
 *
 * <p><b>这个类名不是实现细节，是缺陷的命中前提</b>（091，research.md §5）：组件库里有一条
 * 「含侧边栏的布局 → 其内部的布局容器宽度为 0」的补偿规则（`antd/es/layout/style/index.js`
 * 的 hasSider 分支），它同时把该布局设成**横向**，靠横向可伸缩把被置 0 的宽度长回来。
 * 类名在 ⇒ 规则命中；类名不在 ⇒ 规则根本不触发。
 *
 * <p>而外壳在窄屏把主轴改成了**纵向**（`App.tsx` 的内联 flexDirection），可伸缩于是作用在
 * 高度上，宽度就停在 0 —— 实测内容区可见宽恒为 **24**、内容容器恒为 **0**，
 * 而同一时刻内容本身宽 141~633（渲染完整，只是宽度为 0 所以看不见）。
 * **内联样式能改方向，改不了那条作用在子节点上的 `width: 0`。**
 *
 * <p>⇒「窄屏下该类名不存在」与「内容区宽度不再为 0」是确定的因果关系，
 * 故下面断言它是**因果断言**而非实现耦合。**这也正是它能被证伪的原因**：
 * 把窄屏外壳改回旧写法，该类名重新出现，用例立刻变红。
 */
const hasSiderLayout = () => document.querySelector('.ant-layout-has-sider')

/**
 * 091：窄屏外壳的两条结构判据。
 *
 * <p><b>为什么必须靠结构而不是几何</b>：jsdom **没有布局引擎**，
 * `clientWidth` / `getBoundingClientRect()` 全是假值 ⇒ 缺陷本身（宽度塌陷）在单测里
 * **量不出来**。既有窄屏用例断言的是**文案**（分组被拍平），而文案在缺陷下完全正常，
 * 于是「全仓绿」与「窄屏全站空白」可以同时成立。几何判据走浏览器实测脚本
 * （`specs/091-narrow-shell-collapse/measure-narrow-shell.mjs`），本文件只守结构。
 *
 * <p><b>判据口径上的两条硬约束</b>（实测，research.md §3）：
 * ① `window.innerWidth` 可写、`resize` 可派发、可还原 ⇒ 窄屏分支**能被真的走到**；
 * ② 但 `matchMedia` 被 `src/test/setup.ts` 桩成恒 `matches: false`，
 *    **任何 matchMedia 判据在单测里都会静默失真**——外壳用的是 `innerWidth < 768`，
 *    不是 matchMedia，这是它能被测的前提。
 */
describe('091：窄屏外壳的结构前提', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('accessToken', 'test-token')
    me.role = 'ADMIN'
    me.menus = undefined
  })

  it(
    '窄屏下不被识别为「含侧边栏的布局」，宽屏下被识别',
    async () => {
      const width = window.innerWidth
      try {
        renderWithProviders(<App />, { route: '/leads' })
        await waitFor(() => expect(shellMenu()).not.toBeNull(), { timeout: SHELL_TIMEOUT })

        // 宽屏：菜单与内容区左右并排，布局确实含侧边栏 —— 补偿规则生效，宽度正常。
        expect(hasSiderLayout()).not.toBeNull()

        // 窄屏：主轴转为纵向，此时若仍被识别为「含侧边栏」，那条 width:0 就会永久生效。
        window.innerWidth = 375
        fireEvent(window, new Event('resize'))
        await waitFor(() => expect(hasSiderLayout()).toBeNull(), { timeout: SHELL_TIMEOUT })
      } finally {
        window.innerWidth = width
      }
    },
    SHELL_TIMEOUT * 2,
  )

  it(
    '挂载时窗口就已经是窄屏：同样走窄屏形态（不是「先宽屏再 resize」那条路）',
    async () => {
      const width = window.innerWidth
      // 必须在 render **之前**改：本用例要覆盖的是窄屏判定的**初值**路径，
      // 而既有的那条窄屏用例走的是「先按宽屏渲染、再 resize」，初值从未被覆盖。
      window.innerWidth = 375
      try {
        renderWithProviders(<App />, { route: '/leads' })
        await waitFor(() => expect(shellMenu()).not.toBeNull(), { timeout: SHELL_TIMEOUT })

        // 窄屏形态：不渲染侧边栏组件，布局也不被识别为含侧边栏。
        expect(document.querySelector('.ant-layout-sider')).toBeNull()
        expect(hasSiderLayout()).toBeNull()

        // 菜单仍然在，且是拍平后的一级项 —— 是「换了形态」，不是「菜单消失了」。
        expect(await menuNow().findByText('线索', {}, { timeout: SHELL_TIMEOUT })).toBeTruthy()
        expect(menuNow().queryByText('客户管理')).toBeNull()
      } finally {
        window.innerWidth = width
      }
    },
    SHELL_TIMEOUT * 2,
  )
})
