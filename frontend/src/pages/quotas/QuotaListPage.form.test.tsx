import { describe, expect, it, vi, beforeEach } from 'vitest'
import { configure, screen, within, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import QuotaListPage from './QuotaListPage'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`，与 088 的四个
// `.form.test.tsx` 同一处置）：
// ① `findBy*`/`waitFor` 的等待上限——默认只等 1000ms，全量并发下不够用；
// ② 单条用例上限——仓库默认 20000ms，而本文件每条都要挂载 antd `Modal` + `Form`。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

vi.mock('../../services/api/quotaApi', () => ({
  quotaApi: { list: vi.fn(), getSummary: vi.fn(), create: vi.fn() },
}))

/**
 * 099 · 配额创建从「独立路由页」搬进「列表页内 `FormModal` 弹窗」的行为层证据。
 *
 * <p>为什么这一页必须有新文件：改造前 `QuotaCreatePage` **零单测**、`frontend/e2e/` 下
 * **没有任何用例走创建流程**（配额只被导航型用例访问，从不填表、从不提交），
 * `quotaApi.create` 的请求体**从未被断言过**。所以这 5 条是本项**唯一**的行为层回归证据——
 * 门禁（typecheck/lint/i18n/ui/zh）全都抓不到"弹窗能不能开、提交打没打对、刷没刷新"。
 *
 * <p>五条各自钉住一处真实风险：
 * ① 弹窗契约：标题/6 字段在栅格内、**取消键吃到原语默认值** `common.button.cancel`、
 *    宽度是 `lg` 档 **800px**（改前那页是整页表单，没有宽度这一说；档位是用户裁决的）；
 * ② 校验仍拦得住：必填留空时 `create` **不被调用**，且字段级错误**确实出现**
 *    （只断"没调用"是假绿——那可能只是"还没来得及调用"）；
 * ③ 请求体的形状：`periodStart`/`periodEnd` 必须是 `format('YYYY-MM-DD')` 之后的字符串
 *    （RangePicker 给的是 Dayjs；漏一次 `format` 就会发出 `[object Object]`，而**没有任何门禁看着这里**）；
 * ④ **两条刷新分开断言**：表格重拉（`quotaApi.list` 再被调一次）与 KPI 重取
 *    （`quotaApi.getSummary` 调用次数 +1）**各断一条**。合起来测会让表格刷新掩盖
 *    "KPI 不跟刷"这个真缺陷（旧代码的 `useEffect` 依赖只有 `[year]`，`actionRef` 从未
 *    `reload()` 过 —— 这两条通路本来是**都不存在**的）；
 * ⑤ 失败时弹窗**保持打开**（`catch` 里不许静默关掉，否则用户填的东西无声消失）。
 *
 * <p>三条本仓既有坑（照 088 的先例）：断言**键名**而不是中文（`src/test/setup.ts` 把
 * `t(key)` mock 成键名本身）；取值类断言走 `waitFor`/`findBy*`（`FormModal` 默认
 * `destroyOnClose`，字段是**开弹窗时**才挂载的）；等待上限用共享常量，**不动断言**。
 */

const listRow = {
  id: 1,
  year: 2026,
  quarter: 1,
  teamId: 1,
  userId: 2,
  amount: 10,
  status: 'ACTIVE',
  periodStart: '2026-01-01',
  periodEnd: '2026-03-31',
  createdAt: '2026-01-01T00:00:00',
  teamName: '华东一区',
  userName: '张三',
}

const summary = { totalQuota: 10, totalActual: 4, achievementRate: 40 }

/** antd 把 `width` 作为内联 `style` 写在 `.ant-modal` 上（同 088 的 `CustomerListPage.form.test.tsx` 读法）。 */
const modalWidth = () => (document.querySelector('.ant-modal') as HTMLElement | null)?.style.width ?? ''

async function renderPage() {
  const { quotaApi } = await import('../../services/api/quotaApi')
  vi.mocked(quotaApi.list).mockResolvedValue({ records: [listRow], total: 1 } as never)
  vi.mocked(quotaApi.getSummary).mockResolvedValue(summary as never)
  vi.mocked(quotaApi.create).mockResolvedValue(undefined as never)
  renderWithProviders(<QuotaListPage />)
  // 等表格真的渲染出这一行再往下走 —— 否则后面的断言可能是在"页面还没加载出来"上成立的（假绿）。
  await screen.findByText('华东一区')
}

/** 点工具栏「新建」并返回弹窗。按钮文案仍是改前列表页的那一个键（099 没动它）。 */
async function openCreate() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.quotaList\.btnCreate/ }))
  return await screen.findByRole('dialog')
}

/**
 * 往 `DatePicker.RangePicker` 里填一段区间。
 *
 * 仓里没有第二处用例填过 RangePicker，故记下这里为什么要三步：rc-picker 的输入框
 * **只在自己是当前编辑框时才认 Enter 的提交**，所以每个框都要 `focus` 再 `change` 再
 * `keyDown Enter`；两个框依次填完，`onChange` 才会带着一对 Dayjs 触发。
 */
function fillPeriod(dialog: HTMLElement, start: string, end: string) {
  const inputs = dialog.querySelectorAll<HTMLInputElement>('.ant-picker-input > input')
  expect(inputs.length).toBe(2)
  fireEvent.focus(inputs[0])
  fireEvent.change(inputs[0], { target: { value: start } })
  fireEvent.keyDown(inputs[0], { key: 'Enter', code: 'Enter' })
  fireEvent.focus(inputs[1])
  fireEvent.change(inputs[1], { target: { value: end } })
  fireEvent.keyDown(inputs[1], { key: 'Enter', code: 'Enter' })
}

const FIELD_KEYS = [
  'pages.quotaCreate.formYear',
  'pages.quotaCreate.formQuarter',
  'pages.quotaCreate.formTeamId',
  'pages.quotaCreate.formUserId',
  'pages.quotaCreate.formAmount',
  'pages.quotaCreate.formPeriod',
]

describe('QuotaListPage 的创建弹窗（099）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('点「新建」打开弹窗：6 个字段都在 FormGrid 内，取消键吃默认值，宽度是 lg 档 800px', async () => {
    await renderPage()
    const dialog = await openCreate()

    expect(within(dialog).getByText('pages.quotaCreate.title')).toBeInTheDocument()
    // 6 个字段全在栅格**内**（用 `within(grid)`，让"在栅格内"本身也是被断言的一部分）。
    const grid = within(dialog).getByTestId('form-grid')
    for (const key of FIELD_KEYS) {
      expect(within(grid).getByText(key)).toBeInTheDocument()
    }

    // 页内**没有**传 cancelText ⇒ 渲染出的键名只可能来自 FormModal 的默认值
    //（原页面那个显式的「取消」按钮已随页面删除，这条钉住"改完仍有取消键"）。
    expect(within(dialog).getByText('common.button.cancel')).toBeInTheDocument()
    // 主按钮文案逐字不变：原页面的 `pages.quotaCreate.btnCreate` 搬到了这里。
    expect(within(dialog).getByText('pages.quotaCreate.btnCreate')).toBeInTheDocument()

    // 宽度 = `lg` 档（用户裁决的档位）。读内联 style **属性字符串**而不是 getComputedStyle：
    // antd 把 width 直接写在 `.ant-modal` 上，而 jsdom 的 cssstyle 不反映计算结果。
    expect(modalWidth()).toBe('800px')
  })

  it('必填留空时点「创建配额」：字段级错误出现，且 create 未被调用', async () => {
    await renderPage()
    const { quotaApi } = await import('../../services/api/quotaApi')
    const dialog = await openCreate()

    // 年份有 `initialValues`（当前年）故不是空的；空的是金额与期间。
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.quotaCreate\.btnCreate/ }))

    // 正向信号：字段级错误出现了 —— 它证明 validateFields() 真的跑过并拒绝了。
    // 只断言"没调用 create"是不够的：那可能只是"还没来得及调用"（假绿）。
    expect(await within(dialog).findByText('pages.quotaCreate.msgAmountRequired')).toBeInTheDocument()
    expect(within(dialog).getByText('pages.quotaCreate.msgPeriodRequired')).toBeInTheDocument()
    expect(quotaApi.create).not.toHaveBeenCalled()
  })

  it('填全后提交：请求体里的期间是 YYYY-MM-DD 字符串，年份吃的是 initialValues 的当前年', async () => {
    await renderPage()
    const { quotaApi } = await import('../../services/api/quotaApi')
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.quotaCreate\.formAmount/), {
      target: { value: '12.34' },
    })
    fillPeriod(dialog, '2030-01-01', '2030-12-31')
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.quotaCreate\.btnCreate/ }))

    await waitFor(() => expect(quotaApi.create).toHaveBeenCalledTimes(1))
    const payload = vi.mocked(quotaApi.create).mock.calls[0][0]
    // 逐字段断言而不是整体 `toHaveBeenCalledWith`：`quarter`/`teamId`/`userId` 未填时是
    // `undefined`，而"undefined 键算不算相等"取决于断言库的宽容度——那是个与本次改动无关的变量。
    expect(payload.amount).toBe(12.34)
    expect(payload.year).toBe(new Date().getFullYear())
    // 这一条是**没有门禁看着**的那个点：RangePicker 给的是 Dayjs，漏一次 `format` 就会发出
    // `[object Object]`，而后端只会报参数不合法。
    expect(payload.periodStart).toBe('2030-01-01')
    expect(payload.periodEnd).toBe('2030-12-31')
  })

  it('提交成功后：弹窗关闭，表格重拉，KPI 汇总重取（两条刷新分开断言）', async () => {
    await renderPage()
    const { quotaApi } = await import('../../services/api/quotaApi')
    // 进弹窗前的基线：表格请求 1 次（挂载即拉）、汇总 1 次（`getSummary` 的 useEffect）。
    const listBefore = vi.mocked(quotaApi.list).mock.calls.length
    const summaryBefore = vi.mocked(quotaApi.getSummary).mock.calls.length
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.quotaCreate\.formAmount/), {
      target: { value: '5' },
    })
    fillPeriod(dialog, '2030-01-01', '2030-12-31')
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.quotaCreate\.btnCreate/ }))

    await waitFor(() => expect(quotaApi.create).toHaveBeenCalledTimes(1))
    // ① 弹窗关闭（成功路径必须关掉它，否则用户会再点一次、提交出第二条配额）。
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    // ② 表格重拉：`actionRef.current?.reload()`。
    await waitFor(() => expect(vi.mocked(quotaApi.list).mock.calls.length).toBe(listBefore + 1))
    // ③ KPI 重取：`refreshToken` 自增触发那个 useEffect。**它与②是两条独立通路** ——
    //    098 之前的代码里 `useEffect` 依赖只有 `[year]`、`actionRef` 从未 `reload()` 过，
    //    少了 refreshToken 这条，顶部三张汇总卡会停在旧值上（表格却是新的）。
    await waitFor(() => expect(vi.mocked(quotaApi.getSummary).mock.calls.length).toBe(summaryBefore + 1))
  })

  it('提交失败时：弹窗保持打开（不许静默关掉，用户填的东西不能无声消失）', async () => {
    await renderPage()
    const { quotaApi } = await import('../../services/api/quotaApi')
    vi.mocked(quotaApi.create).mockRejectedValue(new Error('boom'))
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.quotaCreate\.formAmount/), {
      target: { value: '5' },
    })
    fillPeriod(dialog, '2030-01-01', '2030-12-31')
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.quotaCreate\.btnCreate/ }))

    await waitFor(() => expect(quotaApi.create).toHaveBeenCalledTimes(1))
    // 失败提示出现了（`App.useApp()` 的 message；`FormModal` 刻意不吞异常，也不负责提示）。
    expect(await screen.findByText('pages.quotaCreate.msgCreateFailed')).toBeInTheDocument()
    // 且弹窗还在、填的值还在那儿。
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    // 失败不该顺手刷新：两条刷新通路都只在成功分支里。
    expect(vi.mocked(quotaApi.getSummary).mock.calls.length).toBe(1)
  })
})
