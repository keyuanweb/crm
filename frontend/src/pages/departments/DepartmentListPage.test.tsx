import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DepartmentListPage from './DepartmentListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/departmentService', () => ({
  fetchDepartmentTree: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

/**
 * 095 · 本文件承载 `068` 的**三条**任务：T018（搜索）/ T024（创建与编辑）/ T029（删除），
 * 外加 T032（断点二择）与 T037（无障碍）的断言。
 *
 * ## 四条必须知道的口径（免得读数被误读）
 *
 * 1. **`t(key)` 在本仓测试里返回 key 本身**（`src/test/setup.ts` 的 mock，缺键时抛错）。
 *    故断言一律写成 `/pages\.departmentList\.xxx/` 这类正则，**不写中文/英文字面量**。
 * 2. ⚠️ **插值不可观测**：该 mock **忽略第二个参数的插值变量**，因此
 *    `confirmDeleteRisk` 渲染出来就是纯 key —— 本文件能证明「删除确认框挂上了风险说明」，
 *    **不能**证明「文案里的数字等于该节点的 childCount/memberCount」。这条限制如实写在此处，
 *    不假装覆盖。见 `falsification-evidence.md`。
 * 3. ⚠️ **节点名不能用 `getByText` 找**。节点标题是
 *    `<span>📄 <Highlight/></span>`：emoji 与高亮把名字切成了多个元素，而 RTL 的文本匹配
 *    只看元素的**直接文本子节点**（`getNodeText`），祖先 div 的 textContent 反而不参与。
 *    故本文件统一用 `has()` 读**整棵树**的文本 —— 名字仍是完整的业务事实，只是不再假设
 *    它落在一个元素里。
 * 4. ⚠️ **`matchMedia` 的手动桩**：全局桩（`setup.ts`）恒为 `matches: false` ⇒
 *    `Grid.useBreakpoint().lg` 恒假 ⇒ `isMobile` **恒真** ⇒ **桌面分支默认跑不到**。
 *    本文件**不改全局桩**，只在文件内按需覆盖（范式来自 `UsageMapPage.test.tsx`），
 *    并对**移动、桌面各写一条独立断言**——只写一条的话，「用例全绿而桌面分支从未执行」
 *    会被读成两者都验过。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 两棵子树：一棵带子节点（能验展开态），一棵叶子（能验过滤）。 */
const deptTree = [
  {
    id: 1,
    name: '华东销售部',
    parentId: null,
    children: [{ id: 11, name: '上海分部', parentId: 1, children: [], childCount: 0, memberCount: 5, version: 1 }],
    childCount: 1,
    memberCount: 12,
    description: '华东大区',
    sortOrder: 1,
    createdAt: '2026-01-02T03:04:05Z',
    createdBy: 'admin',
    version: 1,
  },
  { id: 2, name: '研发部', parentId: null, children: [], childCount: 0, memberCount: 3, version: 1 },
]

/** 移动桩：与全局桩行为一致（`matches` 恒假）。**显式写出**是为了让用例顺序不影响读数。 */
function stubMobile() {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  })
}

/** 桌面桩：`min-width` 查询命中 ⇒ `lg` 为真 ⇒ `isMobile` 为假。**只在本文件内**。 */
function stubDesktop() {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: query.includes('min-width'),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  })
}

/** 整棵树的文本（口径 3）。 */
const has = (s: string) => (document.body.textContent ?? '').includes(s)
const searchBox = () => screen.getByLabelText(/pages\.departmentList\.ariaSearch/)
const markFor = (text: string) => screen.queryByText(text, { selector: 'mark' })
/** 行内动作按钮：树里每个节点各一个，取**第一个**同名的（即第一个节点那行）。 */
const rowButton = (name: RegExp) => screen.getAllByRole('button', { name })[0]

async function renderPage() {
  const { fetchDepartmentTree } = await import('../../services/departmentService')
  vi.mocked(fetchDepartmentTree).mockResolvedValue(deptTree as never)
  useAuthStore.setState({ user: adminUser })

  renderWithProviders(<DepartmentListPage />)

  // 反空洞守卫：确认页面真的加载完，而不是在空树上做断言。
  await waitFor(() => expect(has('研发部')).toBe(true), { timeout: 5000 })
  expect(screen.getByRole('button', { name: /pages\.departmentList\.btnAdd/ })).toBeInTheDocument()
}

describe('DepartmentListPage 搜索（095 T018/T014/T015/T016）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    stubMobile()
  })

  it('输入关键词后过滤生效，且命中的片段被 <mark> 标出', async () => {
    await renderPage()

    fireEvent.change(searchBox(), { target: { value: '华东' } })

    // 防抖后：只剩华东销售部这棵子树，研发部被过滤掉
    await waitFor(() => expect(has('研发部')).toBe(false), { timeout: 2000 })
    // 高亮断言：mark 元素在场且内容正是关键词
    expect(markFor('华东')).toBeInTheDocument()
  })

  it('300ms 防抖：刚输入时尚未过滤，窗口过后才生效（两段都要断言）', async () => {
    await renderPage()

    fireEvent.change(searchBox(), { target: { value: '研发' } })

    // ① 窗口内：过滤**尚未**发生 —— 子节点仍在、且没有任何高亮。
    //    这一条是「不逐字符重算」在本页的可观测形态（同步断言，不依赖时间流逝）。
    expect(has('上海分部')).toBe(true)
    expect(markFor('研发')).not.toBeInTheDocument()

    // ② 窗口过后：过滤生效，华东销售部整棵被移除（它自己与子节点都不匹配）。
    await waitFor(() => expect(has('上海分部')).toBe(false), { timeout: 2000 })
    expect(markFor('研发')).toBeInTheDocument()
    expect(has('研发部')).toBe(true)
  })

  it('搜不到任何部门时给出空状态（而非一片空白）', async () => {
    await renderPage()

    fireEvent.change(searchBox(), { target: { value: '不存在的部门' } })

    await waitFor(() => expect(screen.getByTestId('page-state-empty')).toBeInTheDocument(), { timeout: 2000 })
    expect(screen.queryByRole('tree')).not.toBeInTheDocument()
    expect(has('研发部')).toBe(false)
  })

  it('清空关键词后恢复「全部展开」（095 T008 的可见行为变化）', async () => {
    await renderPage()

    // 首次加载即展开全部：华东销售部有子节点，其子节点直接在场
    expect(has('上海分部')).toBe(true)

    // 搜到别处：华东子树被过滤掉
    fireEvent.change(searchBox(), { target: { value: '研发' } })
    await waitFor(() => expect(has('上海分部')).toBe(false), { timeout: 2000 })

    // 清空：恢复「全部展开」，而不是旧的 setExpandedKeys([])（那会把子节点收起来）
    fireEvent.change(searchBox(), { target: { value: '' } })
    await waitFor(() => expect(has('上海分部')).toBe(true), { timeout: 2000 })
  })
})

describe('DepartmentListPage 创建与编辑（095 T024）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    stubMobile()
  })

  it('新建：填表保存后调用 createDepartment 并刷新', async () => {
    const { createDepartment, fetchDepartmentTree } = await import('../../services/departmentService')
    vi.mocked(createDepartment).mockResolvedValue(undefined as never)
    await renderPage()
    const callsAfterLoad = vi.mocked(fetchDepartmentTree).mock.calls.length

    fireEvent.click(screen.getByRole('button', { name: /pages\.departmentList\.btnAdd/ }))
    expect(await screen.findByText('pages.departmentList.modalAddTitle')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText(/pages\.departmentList\.formNamePlaceholder/), {
      target: { value: '华南销售部' },
    })
    fireEvent.click(screen.getByRole('button', { name: /pages\.departmentList\.btnSave/ }))

    await waitFor(() => {
      expect(createDepartment).toHaveBeenCalledWith(expect.objectContaining({ name: '华南销售部' }))
    })
    // 保存成功后重新取树（load()），否则列表会停在旧数据上
    await waitFor(() => {
      expect(vi.mocked(fetchDepartmentTree).mock.calls.length).toBeGreaterThan(callsAfterLoad)
    })
  })

  it('编辑：弹窗预填该部门的值，保存时带 id 与 version 调用 updateDepartment', async () => {
    const { updateDepartment } = await import('../../services/departmentService')
    vi.mocked(updateDepartment).mockResolvedValue(undefined as never)
    await renderPage()

    fireEvent.click(rowButton(/pages\.departmentList\.btnEdit/))
    expect(await screen.findByText('pages.departmentList.modalEditTitle')).toBeInTheDocument()
    // 预填：表单里的名称输入框应带出该节点的名称（`version` 一并带上，否则后端会判并发冲突）
    expect(screen.getByPlaceholderText(/pages\.departmentList\.formNamePlaceholder/)).toHaveValue('华东销售部')

    fireEvent.click(screen.getByRole('button', { name: /pages\.departmentList\.btnSave/ }))

    await waitFor(() => {
      expect(updateDepartment).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ name: '华东销售部', version: 1 }),
      )
    })
  })
})

describe('DepartmentListPage 删除（095 T029）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    stubMobile()
  })

  it('删除确认框同时给出标题与风险说明', async () => {
    await renderPage()

    fireEvent.click(rowButton(/pages\.departmentList\.btnDelete/))

    // 精确匹配（不加 Risk）：两串有前缀关系，用正则会同时命中标题与说明而抛 multiple。
    // 标题里的 `{{name}}` 插值被 mock 丢掉，故渲染出来就是纯 key。
    expect(await screen.findByText('pages.departmentList.confirmDelete')).toBeInTheDocument()
    // ⚠️ 只能断言「风险说明挂上了」，不能断言里面的数字（见文件头的口径 2）
    expect(has('pages.departmentList.confirmDeleteRisk')).toBe(true)
  })

  it('确认后调用 deleteDepartment 并刷新', async () => {
    const { deleteDepartment, fetchDepartmentTree } = await import('../../services/departmentService')
    vi.mocked(deleteDepartment).mockResolvedValue(undefined as never)
    await renderPage()
    const callsAfterLoad = vi.mocked(fetchDepartmentTree).mock.calls.length

    fireEvent.click(rowButton(/pages\.departmentList\.btnDelete/))
    // 本页未覆写 okText，故确认键是 antd zh-CN 的默认「确定」（两字按钮会被插空格）
    const ok = await screen.findByRole('button', { name: /确\s*定/ })
    fireEvent.click(ok)

    await waitFor(() => {
      expect(deleteDepartment).toHaveBeenCalledWith(1)
    })
    await waitFor(() => {
      expect(vi.mocked(fetchDepartmentTree).mock.calls.length).toBeGreaterThan(callsAfterLoad)
    })
  })
})

describe('DepartmentListPage 详情与无障碍（095 T030/T032/T037）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('无障碍：搜索框与树容器都有可朗读名称（经 t()，非字面量）', async () => {
    stubMobile()
    await renderPage()

    expect(searchBox()).toBeInTheDocument()
    // ⚠️ 树的标签**落点不在** `role="tree"` 那个元素上：antd 5.22 / rc-tree 5.10 把
    // `aria-label` 透传给了树内部的列表容器（`.ant-tree-list`，实测 outerHTML 已核对）。
    // 故这里断言的是「标签在树**内部**」，**不声称** `role="tree"` 已被命名 ——
    // 那是 antd 的透传位置决定的，本项不做绕过（另造一个 role="tree" 的包装只会更糟）。
    const tree = screen.getByRole('tree')
    expect(within(tree).getByLabelText('pages.departmentList.ariaTree')).toBeInTheDocument()
  })

  it('移动端（全局桩：matches 恒假）：详情走底部抽屉', async () => {
    stubMobile()
    await renderPage()

    fireEvent.click(rowButton(/pages\.departmentList\.btnDetail/))

    await waitFor(() => {
      expect(document.querySelector('.ant-drawer')).toBeTruthy()
    })
    // 反向对照：同一时刻对话框**没有**打开（两个容器都渲染、靠 open 二择）
    expect(document.querySelector('.ant-modal')).toBeNull()
  })

  it('桌面端（本文件内覆盖 matchMedia）：详情走对话框', async () => {
    stubDesktop()
    await renderPage()

    fireEvent.click(rowButton(/pages\.departmentList\.btnDetail/))

    await waitFor(() => {
      expect(document.querySelector('.ant-modal')).toBeTruthy()
    })
    // 反向对照：抽屉**没有**打开。
    // ⚠️ 这条在「把 isMobile 写死为 true」的定向破坏下必须转红 —— 否则说明桌面分支没跑过。
    expect(document.querySelector('.ant-drawer')).toBeNull()
  })
})
