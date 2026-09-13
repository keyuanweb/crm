import { describe, expect, it, vi, beforeEach } from 'vitest'
import { configure, screen, within, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import CustomerListPage from './CustomerListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`）：
// ① `findBy*`/`waitFor` 的等待上限——默认只等 1000ms，全量并发下不够用；
// ② 单条用例上限——仓库默认 20000ms，而本页每条要挂载 antd Modal + Form。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

/*
  同目录另两个文件的 mock 集合：本页要跑完整渲染，缺任一个都会打真接口。
  `useCustomFieldFilters` / `CustomFieldItems` / `utils/customField` 三处被打桩——
  于是「自定义字段」那一块在测试里渲染成 null。这不影响本文件的判据：
  第 2 条钉的是**"全宽项不得进栅格"这条纪律**，而 `<CustomFieldFormItems>` 与备注框
  是同一类（全宽项），备注框还在，足以承担这个判据。
*/
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(),
  createCustomer: vi.fn(),
  updateCustomer: vi.fn(),
  deleteCustomer: vi.fn(),
  fetchMyCustomers: vi.fn(),
  fetchPoolCustomers: vi.fn(),
  batchTransferCustomers: vi.fn(),
  claimCustomer: vi.fn(),
  exportCustomers: vi.fn(),
  importCustomers: vi.fn(),
  downloadTemplate: vi.fn(),
  scanPool: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))
vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
}))
vi.mock('../../hooks/useCustomFieldFilters', () => ({
  useCustomFieldFilterColumns: () => [],
  extractCfParams: () => ({}),
}))
vi.mock('../../components/CustomFieldItems', () => ({
  CustomFieldFormItems: () => null,
}))
vi.mock('../../utils/customField', () => ({
  fromCustomFieldValues: () => ({}),
  toCustomFieldPayload: () => ({}),
}))

/**
 * 088 · `CustomerListPage` 样板转换（P2 T037）的行为层证据。
 *
 * <p>为什么新开文件：`.perm.test.tsx` 管 086 的权限语义（谁看得见「删除」「认领」），
 * `CustomerListPage.test.tsx` 管列表加载与"新建弹窗能提交"这条主路径——**两个文件都从不触碰
 * 栅格、也不断言标签宽度**。于是改完 `FormModal`/`FormGrid` 之后，"7 个字段还在不在同一个
 * 栅格里""标签宽度是不是变成了 96px""第二个（转移）弹窗有没有一起换掉"在本页是**零执行**的。
 *
 * <p>这一页是 P2 四页里**唯一的两弹窗页**，也是 `FormModal` 第二次以 `sm`(480) 之外的档位出现
 * （创建 640 = md）。所以第 7 条专门钉第二个弹窗：它此前既没设宽度（吃 antd 默认 520，R2 的
 * 23 处之一）也没有 `confirmLoading`，是这次唯一**可归因的尺寸变化**（520 → 480，−40px）。
 *
 * <p>七条断言各自对应一处真实改动：
 * ① 创建弹窗换 `FormModal`：7 个成对字段**全在** `form-grid` 内、取消键吃到默认值
 *    `common.button.cancel`（改前是 antd 自带的英文 `Cancel`——本页从未传过 `cancelText`）、
 *    标签宽度 `100px` → **96px**（中文档，本页唯一肉眼可见的尺寸变化）；
 * ② **全宽项在栅格之外**（`FormGrid` 使用纪律第 1 条，也是本组件最可能的误用）；
 * ③ 编辑弹窗的回填在 `destroyOnClose` 下仍生效（`Modal`→`FormModal` 最容易在这里静默坏掉）；
 * ④ `onSubmit` 真的接到了 `onSave`，且校验会拦住提交；
 * ⑤/⑥ 提交落到正确的 service（新建走 `createCustomer`、编辑走 `updateCustomer` + `version`）；
 * ⑦ 第二个弹窗也是 `FormModal`（宽度 480、默认取消键）。
 *
 * <p>用 ADMIN：本文件不涉及权限语义，而 `hasPerm` 对 ADMIN 直通，
 * 于是"工具栏按钮在不在"不会变成干扰变量。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 数据行。字段给全，是为了让"编辑回填"与"编辑提交的 payload"两条断言都有东西可钉。 */
const customerRow = {
  id: 1,
  name: 'Acme 科技',
  company: 'Acme Inc.',
  contactPerson: '张三',
  phone: '13800000000',
  email: 'zhangsan@acme.com',
  address: '上海市',
  remark: '重要客户',
  status: 'ACTIVE',
  ownerName: '销售一',
  version: 0,
}

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const svc = await import('../../services/customerService')
  vi.mocked(svc.fetchCustomers).mockResolvedValue({ items: [customerRow], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(svc.createCustomer).mockResolvedValue({ id: 9, name: '新客户', version: 0 } as never)
  vi.mocked(svc.updateCustomer).mockResolvedValue({ id: 1, name: '核心客户', version: 1 } as never)

  renderWithProviders(<CustomerListPage />)
  // 反空洞守卫：等表格真的渲染出这一行 —— 否则后面的断言可能是在"页面还没加载出来"上成立的。
  await screen.findByText('Acme 科技')
}

/** 点工具栏「新建」并返回弹窗。 */
async function openCreate() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.customer\.list\.create/ }))
  return await screen.findByRole('dialog')
}

/** 点行内「编辑」并返回弹窗。 */
async function openEdit() {
  fireEvent.click(screen.getByText('pages.customer.list.edit'))
  return await screen.findByRole('dialog')
}

/** 先选中一行，再点「批量转移」并返回弹窗。选中是必需的：没选中时只弹一条 warning、不开弹窗。 */
async function openTransfer() {
  const boxes = screen.getAllByRole('checkbox')
  fireEvent.click(boxes[boxes.length - 1])
  const btn = screen.getByRole('button', { name: /pages\.customer\.list\.transfer$/ })
  // 正向信号：按钮真的因"有选中行"而解禁了 —— 否则下面的 findByRole('dialog') 会以
  // 一个与真实原因无关的超时失败。
  await waitFor(() => expect(btn).not.toBeDisabled())
  fireEvent.click(btn)
  return await screen.findByRole('dialog')
}

/** antd 把 `width` 作为内联 `style` 写在 `.ant-modal` 上（同 `FormModal.test.tsx` 的读法）。 */
const modalWidth = () => (document.querySelector('.ant-modal') as HTMLElement | null)?.style.width ?? ''

describe('CustomerListPage 的布局原语转换（088 P2 T037）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('创建弹窗：7 个字段全在 FormGrid 内，取消键吃到默认值，标签宽度 100px → 96px', async () => {
    await renderPage()
    const dialog = await openCreate()

    expect(within(dialog).getByText('pages.customer.list.createModal')).toBeInTheDocument()
    // ① 7 个成对字段一个不少地落在栅格内（原先是被 `<Row gutter={16}>` + 7 个 `<Col span={12}>` 包着的）。
    const grid = within(dialog).getByTestId('form-grid')
    for (const key of ['formName', 'formCompany', 'formContact', 'formPhone', 'formEmail', 'formAddress', 'formCampaign']) {
      expect(within(grid).getByLabelText(new RegExp(`pages\\.customer\\.list\\.${key}`))).toBeInTheDocument()
    }
    // 创建弹窗是 md 档（640px）——与改前的 `width={640}` 逐像素相同。
    expect(modalWidth()).toBe('640px')
    // 页内**没有**传 cancelText，所以渲染出的键名只可能来自 FormModal 的默认值。
    expect(within(dialog).getByText('common.button.cancel')).toBeInTheDocument()
    expect(within(dialog).getByText('common.button.save')).toBeInTheDocument()

    // ② 标签宽度：antd 把 labelCol 落到 `.ant-form-item-label` 这个 Col 的 style 上。
    //    读 style **属性字符串**而不是 getComputedStyle：jsdom 的 cssstyle 不反映 flex 这类简写的计算结果。
    const label = within(dialog).getByText('pages.customer.list.formName')
    const labelColStyle = label.closest('.ant-form-item-label')?.getAttribute('style') ?? ''
    expect(labelColStyle).toContain('96px')
    expect(labelColStyle).not.toContain('100px')
  })

  it('全宽项刻意留在栅格之外：备注框在弹窗里、但不在栅格内，且栅格恰好 7 个子项', async () => {
    await renderPage()
    const dialog = await openCreate()
    const grid = within(dialog).getByTestId('form-grid')

    // 栅格之外的兄弟节点里：备注（全宽长文本；自定义字段块在测试里被打桩成 null，同类）。
    expect(within(grid).queryByLabelText(/pages\.customer\.list\.formRemark/)).toBeNull()
    expect(within(dialog).getByLabelText(/pages\.customer\.list\.formRemark/)).toBeInTheDocument()
    // 子项数**恰好**是 7：这条同时钉住了"将来别把全宽项顺手挪进栅格"——
    // 挪进去它就会悄悄退化成 N 列里的一列，而那不会让任何其它断言转红。
    expect(grid.children).toHaveLength(7)
  })

  it('点行内「编辑」时弹窗回填既有值（setFieldsValue 在 destroyOnClose 下仍生效）', async () => {
    await renderPage()
    const dialog = await openEdit()

    expect(within(dialog).getByText('pages.customer.list.editModal')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/pages\.customer\.list\.formName/)).toHaveValue('Acme 科技')
    // 备注在栅格之外，回填路径与栅格内的字段是两段代码——一并钉住。
    expect(within(dialog).getByLabelText(/pages\.customer\.list\.formRemark/)).toHaveValue('重要客户')
  })

  it('创建弹窗的 onSubmit 接到校验：必填项为空时不提交，并显示字段级错误', async () => {
    await renderPage()
    const { createCustomer } = await import('../../services/customerService')
    const dialog = await openCreate()

    // 名称与公司都是必填，故意留空。
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    // 正向信号：字段级错误出现了 —— 它证明 validateFields() 真的跑过并拒绝了。
    // 只断言"没调用 createCustomer"是不够的：那可能只是"还没来得及调用"（假绿）。
    expect(await within(dialog).findByText('pages.customer.list.msgNameRequired')).toBeInTheDocument()
    expect(within(dialog).getByText('pages.customer.list.msgCompanyRequired')).toBeInTheDocument()
    expect(createCustomer).not.toHaveBeenCalled()
  })

  it('新建提交落到 createCustomer，且只带填了的字段', async () => {
    await renderPage()
    const { createCustomer, updateCustomer } = await import('../../services/customerService')
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.customer\.list\.formName/), { target: { value: '新客户' } })
    fireEvent.change(within(dialog).getByLabelText(/pages\.customer\.list\.formCompany/), { target: { value: '新公司' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    await waitFor(() => expect(createCustomer).toHaveBeenCalledTimes(1))
    // 逐字段断言而不是整体比对：未填字段的值是 `undefined`，而 `{x: undefined}` 与 `{}` 是否相等
    // 取决于断言库对 undefined 键的宽容度——那是个与本次改动无关的变量。
    const payload = vi.mocked(createCustomer).mock.calls[0][0]
    expect(payload.name).toBe('新客户')
    expect(payload.company).toBe('新公司')
    // `editing` 分支没走错：新建被当成编辑是**静默的数据错误**。
    expect(updateCustomer).not.toHaveBeenCalled()
  })

  it('编辑提交落到 updateCustomer(id, {...version})，且带上未改动的既有字段', async () => {
    await renderPage()
    const { createCustomer, updateCustomer } = await import('../../services/customerService')
    const dialog = await openEdit()

    fireEvent.change(within(dialog).getByLabelText(/pages\.customer\.list\.formName/), { target: { value: '核心客户' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    await waitFor(() => expect(updateCustomer).toHaveBeenCalledTimes(1))
    expect(vi.mocked(updateCustomer).mock.calls[0][0]).toBe(1)
    expect(vi.mocked(updateCustomer).mock.calls[0][1]).toMatchObject({
      name: '核心客户',
      company: 'Acme Inc.',
      contactPerson: '张三',
      phone: '13800000000',
      email: 'zhangsan@acme.com',
      version: 0, // 乐观锁：漏了它后端会当成"没带版本"或版本冲突
    })
    expect(createCustomer).not.toHaveBeenCalled()
  })

  it('第二个弹窗（批量转移）也换成了 FormModal：sm 档 480px（改前是 antd 默认 520）、取消键吃默认值', async () => {
    await renderPage()
    const dialog = await openTransfer()

    expect(within(dialog).getByText('pages.customer.list.transferModal')).toBeInTheDocument()
    // 这一条是本页唯一**可归因的尺寸变化**：520 → 480。同时它把 R2（表单弹窗必须显式定宽）
    // 在该页的欠账还清——此前这个 Modal 根本没设宽度。
    expect(modalWidth()).toBe('480px')
    expect(within(dialog).getByText('common.button.cancel')).toBeInTheDocument()
    // 页内传了 okText，不能被 FormModal 的默认值盖掉。
    expect(within(dialog).getByText('pages.customer.list.transferOk')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/pages\.customer\.list\.transferTarget/)).toBeInTheDocument()
  })
})
