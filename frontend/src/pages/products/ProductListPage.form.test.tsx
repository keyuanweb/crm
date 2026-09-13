import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ProductListPage from './ProductListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/productService', () => ({
  fetchProducts: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  deleteProduct: vi.fn(),
}))

vi.mock('../../services/currencyService', () => ({
  fetchCurrencies: vi.fn(),
  createCurrency: vi.fn(),
  updateCurrency: vi.fn(),
  deleteCurrency: vi.fn(),
  convertAmount: vi.fn(),
  fetchProductPrices: vi.fn(),
  setProductPrice: vi.fn(),
  deleteProductPrice: vi.fn(),
}))

/**
 * 088 · `ProductListPage` 样板转换（P2 T035）的行为层证据。
 *
 * <p>为什么这一页需要**新开一个文件**：`.perm.test.tsx` 的 7 个用例全部只查表格文本，
 * **从不打开弹窗**——于是 `FormModal` 的默认脚注、`onSubmit` 接线、`FormGrid` 的渲染、
 * 以及 `editing &&` 那段多币种子区块，在本页是**零执行**的。
 *
 * <p>这一页是 P2 里**唯一需要验证"没有回退"**的一页：它此前是本仓库自己的响应式参考实现
 * （`<Col xs={24} sm={12}>` × 6，全库仅有的 6 个响应式表单 Col）。`FormGrid` 若让它
 * 从两列掉回一列，那是**设计错了**，不是页面错了——所以第 1 条用例专门钉栅格的存在与归属。
 *
 * <p>五条断言各自对应 T035 的一处真实改动或风险：
 * ① 6 个字段都在 `FormGrid` **内**（`within(grid)` 而非 `screen`——字段若只是与栅格并列，
 *    这一组会红），且取消键吃到 `common.button.cancel` 默认值、标签宽走 96px；
 * ② `editing &&` 的子区块只在编辑模式出现（create 模式不该冒出多币种区）；
 * ③ `onSubmit` 真的接到 `onSave`：校验会拦住提交；
 * ④ 新建提交真的落到 `createProduct`，且 `standardPrice` 做了**元 → 分**（×100）——
 *    这是金额口径，落错是静默的金额错误；
 * ⑤ 编辑提交走 `updateProduct(id, {..., version})` 并同步多币种价（057 集成），
 *    且**不会**误调 `createProduct`。
 *
 * <p>用 ADMIN：本文件不涉及权限语义（三码互不串门是 `.perm.test.tsx` 的职责），
 * 而 `hasPerm` 对 ADMIN 直通，于是三个按钮都在、不会变成干扰变量。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

const productRow = {
  id: 1,
  code: 'CRM-STD',
  name: '标准产品',
  spec: '标准规格',
  unit: '个',
  standardPrice: 10000,
  status: 'ACTIVE',
  version: 3,
}

/** 已配置的 USD 价：720 分 → 7.20 元。 */
const configuredPrice = { currencyCode: 'USD', price: 720, converted: 720, configured: true }

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const product = await import('../../services/productService')
  const currency = await import('../../services/currencyService')
  vi.mocked(product.fetchProducts).mockResolvedValue({ items: [productRow], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(product.createProduct).mockResolvedValue(undefined as never)
  vi.mocked(product.updateProduct).mockResolvedValue(undefined as never)
  vi.mocked(currency.fetchCurrencies).mockResolvedValue([
    { code: 'CNY', name: '人民币', rate: 1, isBase: true },
    { code: 'USD', name: '美元', rate: 7.2, isBase: false },
  ] as never)
  vi.mocked(currency.fetchProductPrices).mockResolvedValue({
    productId: 1,
    basePrice: 10000,
    prices: [configuredPrice],
  } as never)
  vi.mocked(currency.setProductPrice).mockResolvedValue(undefined as never)
  vi.mocked(currency.deleteProductPrice).mockResolvedValue(undefined as never)

  renderWithProviders(<ProductListPage />)
  // 反空洞守卫：等表格真的渲染出这一行，后面的否定断言才有意义。
  await screen.findByText(productRow.name)
}

/** 工具栏「新增产品」。 */
async function openCreate() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.product\.list\.create$/ }))
  return await screen.findByRole('dialog')
}

/** 行内「编辑」。 */
async function openEdit() {
  fireEvent.click(screen.getByText('pages.product.list.edit'))
  return await screen.findByRole('dialog')
}

const LABELS = [
  'pages.product.list.formCode',
  'pages.product.list.formName',
  'pages.product.list.formSpec',
  'pages.product.list.formUnit',
  'pages.product.list.formPrice',
  'pages.product.list.formStatus',
]

describe('ProductListPage 的布局原语转换（088 P2 T035）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('6 个字段都在 FormGrid 内（替代此前的响应式 Col），取消键走默认值，标签宽 96px', async () => {
    await renderPage()
    const dialog = await openCreate()

    const grid = within(dialog).getByTestId('form-grid')
    // 用 `within(grid)` 而不是 `screen`：这样"字段在栅格**内**"本身也是被断言的一部分。
    for (const label of LABELS) {
      expect(within(grid).getByText(label)).toBeInTheDocument()
    }

    // 页内**没有**传 cancelText ⇒ 渲染出的键名只可能来自 FormModal 的默认值
    //（改前是 antd 自带的英文 `Cancel`）。
    expect(within(dialog).getByText('common.button.cancel')).toBeInTheDocument()

    // 标签宽 110px → useFormMetrics 的 96px（中文档）。读 style **属性字符串**而非
    // getComputedStyle：antd 把 labelCol 直接写在 `.ant-form-item-label` 这个 Col 的
    // style 上，而 jsdom 的 cssstyle 不反映 flex 这类简写的计算结果。
    const labelColStyle = within(dialog).getByText('pages.product.list.formCode').closest('.ant-form-item-label')?.getAttribute('style') ?? ''
    expect(labelColStyle).toContain('96px')
    expect(labelColStyle).not.toContain('110px')
  })

  it('多币种子区块只在编辑模式出现（create 模式不该冒出来）', async () => {
    await renderPage()

    const createDialog = await openCreate()
    expect(within(createDialog).queryByText('pages.product.list.multiPriceTitle')).not.toBeInTheDocument()

    fireEvent.click(within(createDialog).getByText('common.button.cancel'))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    const editDialog = await openEdit()
    expect(await within(editDialog).findByText('pages.product.list.multiPriceTitle')).toBeInTheDocument()
    // 已配置的 USD 价被回填成一行（720 分 → 7.20 元）。显示值是 **`7.20`** 而不是 `7.2`：
    // 那个 InputNumber 带 `precision={2}`，rc-input-number 按精度补零后才是屏上的字面量。
    expect(await within(editDialog).findByDisplayValue('7.20')).toBeInTheDocument()
  })

  it('创建弹窗的 onSubmit 接到校验：必填项为空时不提交，并显示字段级错误', async () => {
    await renderPage()
    const { createProduct } = await import('../../services/productService')
    const dialog = await openCreate()

    // 三个必填（code / name / standardPrice）全空。
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    // 正向信号：字段级错误出现了 —— 它证明 validateFields() 真的跑过并拒绝了。
    // 只断言"没调用 createProduct"是不够的：那可能只是"还没来得及调用"（假绿）。
    expect(await within(dialog).findByText('pages.product.list.msgCodeRequired')).toBeInTheDocument()
    expect(createProduct).not.toHaveBeenCalled()
  })

  it('新建提交落到 createProduct，且 standardPrice 做了元 → 分', async () => {
    await renderPage()
    const product = await import('../../services/productService')
    const currency = await import('../../services/currencyService')
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.product\.list\.formCode/), { target: { value: '  CRM-PRO  ' } })
    fireEvent.change(within(dialog).getByLabelText(/pages\.product\.list\.formName/), { target: { value: '专业版' } })
    fireEvent.change(within(dialog).getByLabelText(/pages\.product\.list\.formPrice/), { target: { value: '199.99' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    await waitFor(() => expect(product.createProduct).toHaveBeenCalledTimes(1))
    const payload = vi.mocked(product.createProduct).mock.calls[0][0]
    // trim 与 ×100 都在页面里做：`199.99` → 19999 分（浮点乘 100 得 19998.999…，故必须 round）。
    expect(payload.code).toBe('CRM-PRO')
    expect(payload.name).toBe('专业版')
    expect(payload.standardPrice).toBe(19999)
    // 新建分支**不碰**多币种接口（同步逻辑只在 editing 分支里）。
    expect(currency.setProductPrice).not.toHaveBeenCalled()
    expect(currency.deleteProductPrice).not.toHaveBeenCalled()
  })

  it('编辑提交走 updateProduct(id, {..., version})，并同步多币种价（057）', async () => {
    await renderPage()
    const product = await import('../../services/productService')
    const currency = await import('../../services/currencyService')
    const dialog = await openEdit()
    // 等回填完成再改（`fetchProductPrices` 是后到的 promise；`openEdit` 不会等它）。
    await within(dialog).findByDisplayValue('7.20')

    fireEvent.change(within(dialog).getByLabelText(/pages\.product\.list\.formName/), { target: { value: '标准产品（改）' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /common\.button\.save/ }))

    await waitFor(() => expect(product.updateProduct).toHaveBeenCalledTimes(1))
    const [id, payload] = vi.mocked(product.updateProduct).mock.calls[0]
    expect(id).toBe(1)
    // version 必须带上：后端靠它做乐观锁，漏了就是"静默覆盖他人的修改"。
    expect(payload.version).toBe(3)
    expect(payload.name).toBe('标准产品（改）')
    // 保留行按当前值重新提交（7.2 元 → 720 分），已配置币种未被移除 ⇒ 不调 deleteProductPrice。
    expect(currency.setProductPrice).toHaveBeenCalledWith(1, 'USD', 720)
    expect(currency.deleteProductPrice).not.toHaveBeenCalled()
    // `editing` 分支没走错：改一版被当成新建是**静默的数据错误**。
    expect(product.createProduct).not.toHaveBeenCalled()
  })
})
