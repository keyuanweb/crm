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
      const menu = await nav()
      // 宽屏：分组以可折叠 submenu 呈现（分组标题可见）
      expect(menu.getByText('客户管理')).toBeTruthy()

      try {
        window.innerWidth = 375
        fireEvent(window, new Event('resize'))
        // 窄屏不支持分组：分组标题整体消失……
        await waitFor(() => expect(menu.queryByText('客户管理')).toBeNull(), {
          timeout: SHELL_TIMEOUT,
        })
        // ……组内的项升为一级项，仍然可见（「拍平」而不是「隐藏」）
        expect(await menu.findByText('线索', {}, { timeout: SHELL_TIMEOUT })).toBeTruthy()
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

      // 语言：切到 English 再切回中文（文案是字面量，不受 i18n 是否加载影响），
      // 并以 localStorage 的持久化结果为证；结束时恢复中文，避免污染同文件其他用例。
      openUserMenu()
      fireEvent.click(screen.getAllByText('English')[0])
      await waitFor(() => expect(localStorage.getItem('app_lang')).toBe('en'))
      openUserMenu()
      fireEvent.click(screen.getAllByText('中文')[0])
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

    fireEvent.click(screen.getByLabelText('折叠/展开菜单'))
    await waitFor(() => expect(sider()?.className).toContain('ant-layout-sider-collapsed'))

    fireEvent.click(screen.getByLabelText('折叠/展开菜单'))
    await waitFor(() => expect(sider()?.className).not.toContain('ant-layout-sider-collapsed'))
  })
})
