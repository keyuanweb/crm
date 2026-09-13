import { describe, expect, it, vi, beforeEach } from 'vitest'
import { configure, screen, within, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import InvoiceListPage from './InvoiceListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`）：
// ① `findBy*`/`waitFor` 的等待上限——默认只等 1000ms，全量并发下不够用；
// ② 单条用例上限——仓库默认 20000ms，而本页每条要挂载 antd Modal + Form。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

vi.mock('../../services/invoiceService', () => ({
  fetchInvoices: vi.fn(),
  createInvoice: vi.fn(),
  voidInvoice: vi.fn(),
  fetchInvoiceStats: vi.fn(),
  fetchOrdersForInvoice: vi.fn(),
}))

/**
 * 088 · `InvoiceListPage` 样板转换（P2 T031）的行为层证据。
 *
 * <p>为什么这一页需要**新开一个文件**而不是扩 `.perm.test.tsx`：那个文件管的是 086 的
 * 权限语义（谁看得见「作废」），这里管的是 088 的布局原语——**受众不同、失效原因不同**。
 * 混在一起会让"权限用例红了"与"表单弹窗红了"指向同一个文件，归因变难。
 *
 * <p>为什么必须有这几条：`.perm.test.tsx` 的三个用例**从不打开弹窗**，
 * 于是 `FormModal` 的默认脚注、`onSubmit` 接线与 `FormGrid` 的渲染在本页是**零执行**的——
 * 不做这一条，"弹窗改成 FormModal 之后还能不能开、还能不能提交"就只能靠肉眼。
 *
 * <p>四条断言各自对应 T031 的一处真实改动：
 * ① 三个手搓 `<div>` + `<Statistic>` → `StatCard`：三个标签仍在、格式化仍发生在调用点；
 * ② 创建弹窗 → `FormModal` + `FormGrid`：栅格在弹窗内、且 5 个字段都在栅格**内**；
 * ③ `FormModal` 的 `cancelText` 默认值生效：取消键渲染出 `common.button.cancel`。
 *    这条的证伪力来自"antd 自己的默认值是英文 `Cancel`"——看到这个键名**只能**是
 *    FormModal 的默认值接管了，不可能是"恰好没渲染"；
 * ④ `FormModal` 的 `onSubmit` 真的接到了 `onCreate` / `onVoid`：前者证明校验会拦住提交，
 *    后者证明提交真的落到了 service 上。
 *
 * <p>用 ADMIN：本文件不涉及权限语义，而 `hasPerm` 对 ADMIN 直通，
 * 于是"行内作废链接在不在"不会变成干扰变量。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

const issuedInvoice = {
  id: 1,
  orderId: 100,
  orderNo: 'SO-2026-001',
  invoiceNo: 'INV-2026-001',
  title: 'Acme 科技首期款',
  amount: 123456,
  invoiceType: 'GENERAL',
  status: 'ISSUED',
  issuedAt: '2026-02-03T10:20:30',
}

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const { fetchInvoices, fetchInvoiceStats, fetchOrdersForInvoice } = await import('../../services/invoiceService')
  vi.mocked(fetchInvoices).mockResolvedValue({ items: [issuedInvoice], total: 1, page: 1, pageSize: 10 } as never)
  vi.mocked(fetchInvoiceStats).mockResolvedValue({
    // 350000 是特意取的：它 → 3500.00 元，与表格行金额（123456 分 → 1234.56 元）不同，
    // 于是三个统计值都能**唯一**定位。若两处数字相同，`getByText` 撞上多个元素会直接抛错。
    totalInvoiceAmount: 350000,
    totalOrderAmount: 200000,
    invoiceRate: 61.7,
    byOrder: [],
  } as never)
  vi.mocked(fetchOrdersForInvoice).mockResolvedValue([] as never)
  renderWithProviders(<InvoiceListPage />)
  // 等表格真的渲染出发票号再往下走 —— 否则后面的断言可能是在"页面还没加载出来"上成立的（假绿）。
  await screen.findByText('INV-2026-001')
}

/** 按弹窗标题取弹窗：两个弹窗可能同时在屏上，故不依赖 portal 的挂载顺序。 */
const dialogWithTitle = (title: string) => screen.getAllByRole('dialog').find((d) => within(d).queryByText(title))

describe('InvoiceListPage 的布局原语转换（088 P2 T031）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('三个统计块换成 StatCard 后：首屏就加载出真实值，且格式化仍发生在调用点', async () => {
    await renderPage()

    expect(screen.getByText('pages.invoiceList.statCards.invoiceRate')).toBeInTheDocument()
    expect(screen.getByText('pages.invoiceList.statCards.totalInvoiceAmount')).toBeInTheDocument()
    expect(screen.getByText('pages.invoiceList.statCards.totalOrderAmount')).toBeInTheDocument()

    // 首屏就要有值：`loadStats` 现在由一次挂载期 `useEffect` 调用。
    // **这条断言此前是 `0%` 与两个 `0.00`** —— 那时 `loadStats` 只被 `reload()` 调用，
    // 而 `reload()` 只被 onCreate/onVoid 调用，首屏恒为 0（research.md §11.4）。
    // 用 `findByText` 而不是 `getByText`：统计与表格是两条独立的 promise 链，
    // "表格渲染出发票号"并不代表统计已经落地。
    expect(await screen.findByText('3500.00')).toBeInTheDocument()
    // 350000 分 → 3500.00 元、200000 分 → 2000.00 元；比率带 % 后缀。
    expect(screen.getByText('2000.00')).toBeInTheDocument()
    expect(screen.getByText('61.7%')).toBeInTheDocument()
    // 格式化仍发生在调用点：`StatCardProps.value` 是 `string | number`，
    // 传进去的是已经 `toFixed(2)` 好的字符串，组件自身不做数值格式化——
    // 这条是"金额口径没有被藏进组件"的证据。
  })

  it('创建弹窗里渲染出 FormGrid，且 5 个字段都在栅格内', async () => {
    await renderPage()

    fireEvent.click(screen.getByRole('button', { name: /pages\.invoiceList\.toolbar\.invoice/ }))

    const dialog = await screen.findByRole('dialog')
    const grid = within(dialog).getByTestId('form-grid')
    // 用 `within(grid)` 而不是 `screen`：这样"字段在栅格**内**"本身也是被断言的一部分——
    // 字段若只是与栅格并列（即 FormGrid 没包住它们），这一组会红。
    for (const label of [
      'pages.invoiceList.form.order',
      'pages.invoiceList.form.title',
      'pages.invoiceList.form.taxNo',
      'pages.invoiceList.form.amount',
      'pages.invoiceList.form.invoiceType',
    ]) {
      expect(within(grid).getByText(label)).toBeInTheDocument()
    }
  })

  it('创建弹窗与作废弹窗的取消键都吃到了 FormModal 的 cancelText 默认值', async () => {
    await renderPage()

    // ① 创建弹窗：页内**没有**传 cancelText，所以渲染出的键名只可能来自 FormModal 的默认值。
    fireEvent.click(screen.getByRole('button', { name: /pages\.invoiceList\.toolbar\.invoice/ }))
    const createDialog = await screen.findByRole('dialog')
    expect(within(createDialog).getByText('common.button.cancel')).toBeInTheDocument()

    // ② 作废弹窗：`voidRow` 置位后才 open，故先点行内「作废」链接。
    fireEvent.click(screen.getByText('pages.invoiceList.status.void'))
    const voidDialog = dialogWithTitle('pages.invoiceList.modal.voidInvoice')
    expect(voidDialog).toBeDefined()
    const inVoid = within(voidDialog as HTMLElement)
    expect(inVoid.getByText('common.button.cancel')).toBeInTheDocument()
    // OK 键的文案也保住了（防止"换 FormModal 时把 okText 丢了"退回 antd 默认的 OK）。
    expect(inVoid.getByText('pages.invoiceList.modal.voidOk')).toBeInTheDocument()
  })

  it('创建弹窗的 onSubmit 接到校验：必填项缺失时不提交，并显示字段级错误', async () => {
    await renderPage()

    const { createInvoice } = await import('../../services/invoiceService')
    fireEvent.click(screen.getByRole('button', { name: /pages\.invoiceList\.toolbar\.invoice/ }))
    const dialog = await screen.findByRole('dialog')

    // 只填 title，故意空着必填的 orderId（Select）——Select 在 jsdom 里难交互，
    // 而"校验拦住提交"这件事用**空着**来证反而更直接。
    fireEvent.change(within(dialog).getByLabelText(/pages\.invoiceList\.form\.title/), { target: { value: '测试' } })
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.invoiceList\.modal\.createOk/ }))

    // 正向信号：字段级错误出现了 —— 它证明 validateFields() 真的跑过并拒绝了。
    // 只断言"没调用 createInvoice"是不够的：那可能只是"还没来得及调用"（假绿）。
    expect(await within(dialog).findByText('pages.invoiceList.form.orderRequired')).toBeInTheDocument()
    expect(createInvoice).not.toHaveBeenCalled()
  })

  it('作废弹窗的 onSubmit 接到 onVoid：提交落到 service，且随后的 reload 会重新拉统计', async () => {
    await renderPage()

    const { voidInvoice, fetchInvoiceStats } = await import('../../services/invoiceService')
    // 先记下挂载期那次加载的次数：断言"又拉了一次"而不是"屏上有值"——
    // 挂载期已经让屏上有值了，再断言值就证明不了 reload 真的跑过。
    const statsCallsBefore = vi.mocked(fetchInvoiceStats).mock.calls.length

    fireEvent.click(screen.getByText('pages.invoiceList.status.void'))
    const voidDialog = dialogWithTitle('pages.invoiceList.modal.voidInvoice') as HTMLElement

    fireEvent.change(within(voidDialog).getByPlaceholderText('pages.invoiceList.voidForm.reasonPlaceholder'), {
      target: { value: '抬头开错' },
    })
    fireEvent.click(within(voidDialog).getByRole('button', { name: /pages\.invoiceList\.modal\.voidOk/ }))

    await waitFor(() => expect(voidInvoice).toHaveBeenCalledWith(1, '抬头开错'))
    await waitFor(() => expect(vi.mocked(fetchInvoiceStats).mock.calls.length).toBeGreaterThan(statsCallsBefore))
  })
})
