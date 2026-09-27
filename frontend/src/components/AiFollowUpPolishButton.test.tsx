import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import AiFollowUpPolishButton from './AiFollowUpPolishButton'
import {
  generateCustomerSummary,
  generateEmailDraft,
  generateFollowUpPolish,
} from '../services/aiContentService'
import type { FollowUpPolish } from '../types/aiContent'
import zhCN from '../i18n/zh-CN'
import en from '../i18n/en'

vi.mock('../services/aiContentService', () => ({
  generateFollowUpPolish: vi.fn(),
  generateEmailDraft: vi.fn(),
  generateCustomerSummary: vi.fn(),
}))

/**
 * 104 · `AiFollowUpPolishButton`（P3 跟进记录润色 / 总结）——**只钉 P3 自己那几件**。
 *
 * <p><b>四态、失败态保留编辑区、错误码 ⇒ 文案的分派**全在共用外壳 `AiTextGenerateButton` 里，已由
 * `AiGenerateButton.test.tsx` 逐条看着；此处不抄第二遍（两份表述会各自漂移）。本文件的判据是
 * **外壳看不见的那一面**：
 *
 * <ol>
 *   <li><b>G-a/G-b 请求体搭对了没有</b>：调的是 `generateFollowUpPolish`、请求体恰为
 *       `{content, mode, customerId?}`——`customerId` **只在宿主给了的时候**才出现（P3 的宿主
 *       `FollowUpTimeline` 同时挂在客户详情页与线索详情页上，线索页不声明客户归属）。这是本组件最容易
 *       被接错的一处：外壳只拿到一个闭包、**看不见请求体**（见外壳类注释）。
 *   <li><b>G-c `mode` 是状态、随选择器变</b>：`mode` 是请求体的一部分，其状态住在 P3 自己的组件里
 *       （外壳只给一块 `extraControls` 的位置）。默认 `POLISH`（改动最小的那个模式）。
 *   <li><b>G-d/G-e 空原文 ⇒ 按钮禁用</b>：这不是入参校验的替身（长度上限仍由后端判），而是"点了也
 *       白点"——空输入必然回 400，而那个 400 的文案对着一块本来就空的编辑区说"输入不合法"是句废话。
 *       ⚠️ 正对照 G-e 是必需的：只有 G-d 的话，"恒禁用"这种也能让它绿。
 *   <li><b>G-f `BAD_REQUEST` ⇒ `InvalidInput`</b>：P3 是三个端点里**唯一**会回 400 的（见
 *       `aiContentService.generateFollowUpPolish` 的注释）。它的下一步与 403 不同——用户要改的是
 *       内容本身，不是等一会儿或找管理员。
 *   <li><b>G-j 键契约</b>：`check-i18n.mjs` 只比对两份语言文件之间的键集合，**不扫源码用法**；
 *       而测试的 `t` 桩只校验 zh-CN 一侧。故"某条后缀在 en 里缺失"没有任何别的判据看得见。
 * </ol>
 *
 * <p>⚠️ 文案键的**作用域是 `pages.followUpTimeline`**（宿主自己那一组），不是 `pages.customer.detail`
 * ——把 P3 的键塞进客户页那组，会让线索页上的按钮读客户页的词条，而两份语言文件都有那些键、
 * 门禁与 `t` 桩都不会响。故 G-f/G-g 断言的是**完整键名字面量**（含作用域），不是"某句话出现了"。
 *
 * <p>⚠️ 超时显式放到 60s（照本仓对重文件的既有做法）：每条用例都要渲染一次 antd `Modal`
 * （内含 `Input.TextArea` 与一组 `Radio`），默认 20s 在这台机器上会被并行 worker 抢到翻车，
 * 而那是**假的失败**。
 */
const KEY = 'pages.followUpTimeline'
const PREFIX = 'aiPolish'
const PLACEHOLDER = `${KEY}.${PREFIX}Placeholder`
const MODE_LABEL = `${KEY}.${PREFIX}ModeLabel`
const MODE_POLISH = `${KEY}.${PREFIX}ModePolish`
const MODE_SUMMARIZE = `${KEY}.${PREFIX}ModeSummarize`

/** 一段**带日期**的跟进原文：日期是 P3 的判据（T064 定向破坏的目标），故测试数据里必须有一个。 */
const CONTENT = '3 月 5 日与张经理通了电话，他说预算要等下一季度。'

const mockPolish = vi.mocked(generateFollowUpPolish)
const mockDraft = vi.mocked(generateEmailDraft)
const mockSummary = vi.mocked(generateCustomerSummary)

function polish(overrides: Partial<FollowUpPolish> = {}): FollowUpPolish {
  return { text: '整理后的跟进记录', model: 'claude-opus-5', truncated: false, ...overrides }
}

/**
 * 一个后端受控错误。
 *
 * <p>⚠️ **必须带 `isAxiosError: true`**：`extractErrorCode` 走 `axios.isAxiosError`，缺这个标记时
 * 它会返回 `undefined`，于是按码分派的断言会落到通用文案上——用例会以"文案不匹配"红掉，指向的
 * 却是桩的形状，而不是被测代码（`AiGenerateButton.test.tsx` 已为 P1 记过同一坑）。
 */
function apiError(code: string, status: number) {
  return {
    isAxiosError: true,
    message: `Request failed with status code ${status}`,
    response: { status, data: { success: false, error: { code, message: '后端文案' } } },
  }
}

/** 渲染按钮并打开模态框，返回编辑区（textarea）。 */
async function openModal(
  props: { content?: string; customerId?: number; onGenerated?: (text: string) => void } = {},
): Promise<HTMLElement> {
  renderWithProviders(
    <AiFollowUpPolishButton
      content={props.content ?? CONTENT}
      customerId={props.customerId}
      onGenerated={props.onGenerated}
    />,
  )
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Button`) }))
  return screen.findByPlaceholderText(PLACEHOLDER)
}

/** 按钮在同一次渲染里的可访问名（触发按钮，不是模态框里的"生成"）。 */
function trigger(): HTMLElement {
  return screen.getByRole('button', { name: new RegExp(`${PREFIX}Button`) })
}

describe('AiFollowUpPolishButton（104 P3 跟进记录润色 / 总结）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it(
    'G-a 成功 ⇒ 调的是 followup-polish、请求体恰为 {content,mode,customerId}、且不碰另两个能力',
    async () => {
      mockPolish.mockResolvedValue(polish())
      const onGenerated = vi.fn()
      const box = await openModal({ customerId: 9, onGenerated })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      // ⚠️ 顺序照 P1 的 D5-b / P2 的 F5-a 那条教训：**点名缺陷的断言排在状态断言之前**。
      // 接错函数 ⇒ "Number of calls: 0"；请求体多/少字段 ⇒ 逐字相等失败并打出实际入参。
      await waitFor(() =>
        expect(mockPolish).toHaveBeenCalledWith({ content: CONTENT, mode: 'POLISH', customerId: 9 }),
      )
      expect(mockPolish).toHaveBeenCalledTimes(1)
      // 反向判据：接错端点在类型上完全合法（`EmailDraftRequest` 与 `CustomerSummaryRequest` 也都能
      // 接受一个 `{customerId}`），只有这一行看得住
      expect(mockDraft).not.toHaveBeenCalled()
      expect(mockSummary).not.toHaveBeenCalled()
      // 宿主契约：每次成功恰好回调一次，参数是返回的**原始**文本
      await waitFor(() => expect(onGenerated).toHaveBeenCalledTimes(1))
      expect(onGenerated).toHaveBeenCalledWith('整理后的跟进记录')
      expect(box).toHaveValue('整理后的跟进记录')
    },
    60_000,
  )

  it(
    'G-b 宿主没给客户（线索页）⇒ 请求体里**没有** customerId 这个键，而不是带一个空值',
    async () => {
      mockPolish.mockResolvedValue(polish())
      await openModal({ customerId: undefined })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      await waitFor(() => expect(mockPolish).toHaveBeenCalledTimes(1))
      // 逐字相等（不是"包含"）：多带一个 `customerId: undefined`、`tone`、`instruction` 都会红
      expect(mockPolish).toHaveBeenCalledWith({ content: CONTENT, mode: 'POLISH' })
      // ⚠️ 上面那行**判不到"键在不在"**：`toHaveBeenCalledWith` 的相等比较忽略取值为 `undefined`
      // 的属性，而"带一个 undefined 的 id"与"不声明归属"在后端是**两件事**（提供即须在可见范围内）。
      // 线上真正过去的是 JSON，故这里断言的就是被序列化后的那串字节——axios 用 `JSON.stringify`，
      // 它会把 `undefined` 值的键整个丢掉，`.not.toContain` 因此是真的判据。
      expect(JSON.stringify(mockPolish.mock.calls[0][0])).not.toContain('customerId')
    },
    60_000,
  )

  it(
    'G-c 模式选择器：默认 POLISH、切成"总结"后出站就是 SUMMARIZE（且不重发）',
    async () => {
      mockPolish.mockResolvedValue(polish())
      await openModal({ customerId: 9 })

      // 选择器的三条键与按钮同作用域（P3 独有的三条）
      expect(screen.getByText(MODE_LABEL)).toBeInTheDocument()
      expect(screen.getByText(MODE_POLISH)).toBeInTheDocument()

      fireEvent.click(screen.getByLabelText(MODE_SUMMARIZE))
      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      await waitFor(() =>
        expect(mockPolish).toHaveBeenCalledWith({
          content: CONTENT,
          mode: 'SUMMARIZE',
          customerId: 9,
        }),
      )
      // 换模式**不**触发第二次出站：出站只由"生成"按钮驱动（模式是下一次请求体的内容，不是一次调用）
      expect(mockPolish).toHaveBeenCalledTimes(1)
    },
    60_000,
  )

  it(
    'G-d 原文为空（含只敲了空格）⇒ 触发按钮禁用，模态框打不开',
    async () => {
      renderWithProviders(<AiFollowUpPolishButton content={'  \n '} customerId={9} />)
      const btn = trigger()

      expect(btn).toBeDisabled()
      // 点下去也白点：模态框（及其编辑区）不该出现——空输入必然回 400，那个 400 的文案对着
      // 一块本来就空的编辑区说"输入不合法"是句废话。
      fireEvent.click(btn)
      expect(screen.queryByPlaceholderText(PLACEHOLDER)).not.toBeInTheDocument()
      expect(mockPolish).not.toHaveBeenCalled()
    },
    60_000,
  )

  it(
    'G-e 正对照：原文非空 ⇒ 按钮可用（否则"恒禁用"也能让 G-d 绿）',
    async () => {
      renderWithProviders(<AiFollowUpPolishButton content={CONTENT} customerId={9} />)

      expect(trigger()).toBeEnabled()
      fireEvent.click(trigger())
      expect(await screen.findByPlaceholderText(PLACEHOLDER)).toBeInTheDocument()
    },
    60_000,
  )

  it(
    'G-f 输入不合法（400 BAD_REQUEST）⇒ 用 InvalidInput 文案，不冒充通用失败',
    async () => {
      mockPolish.mockRejectedValue(apiError('BAD_REQUEST', 400))
      await openModal({ customerId: 9 })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      expect(await screen.findByText(`${KEY}.${PREFIX}InvalidInput`)).toBeInTheDocument()
      // 通用失败文案是"等会儿再试"，而 400 是"这次输入不成立、重试无用"——两者混用会让用户
      // 对着一个输入错误一直点重试
      expect(screen.queryByText(`${KEY}.${PREFIX}Failed`)).not.toBeInTheDocument()
    },
    60_000,
  )

  it(
    'G-g 数据范围拒（403 FORBIDDEN）⇒ P3 那组键（作用域是 pages.followUpTimeline）',
    async () => {
      mockPolish.mockRejectedValue(apiError('FORBIDDEN', 403))
      await openModal({ customerId: 9 })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      // ⚠️ 完整键名（含作用域）：宿主的这个组件也挂在**线索**详情页上，把 P3 的词条写进
      // `pages.customer.detail` 会让线索页读客户页的词条，而 `check-i18n.mjs` 与 `t` 桩都不会响
      expect(await screen.findByText(`${KEY}.${PREFIX}Forbidden`)).toBeInTheDocument()
      expect(
        screen.queryByText(`pages.customer.detail.${PREFIX}Forbidden`),
      ).not.toBeInTheDocument()
    },
    60_000,
  )

  it(
    'G-h 截断 ⇒ 截断提示，且不得同时呈现为"生成完成"',
    async () => {
      mockPolish.mockResolvedValue(polish({ text: '半截的记录', truncated: true }))
      const box = await openModal({ customerId: 9 })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      expect(await screen.findByText(`${KEY}.${PREFIX}Truncated`)).toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.${PREFIX}Done`)).not.toBeInTheDocument()
      // 半截文本仍要落在编辑区（用户得拿它续写），但上面两条才是结论：它是"未完成"的
      await waitFor(() => expect(box).toHaveValue('半截的记录'))
    },
    60_000,
  )

  it(
    'G-i 无码的失败 ⇒ 走通用文案，且出站身份仍被点名',
    async () => {
      // 非 axios 错误（超时/断网/CORS 都长这样）：extractErrorCode 返回 undefined，
      // extractErrorMessage 对非 axios 错误**直接返回 fallback**（apiClient.ts:94），
      // 即组件传进去的 t(k('Failed'))——测试里 t 是恒等函数，故断言的是键名。
      mockPolish.mockRejectedValue(new Error('Network Error'))
      await openModal({ customerId: 9 })

      fireEvent.click(screen.getByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      expect(await screen.findByText(`${KEY}.${PREFIX}Failed`)).toBeInTheDocument()
      // 通用文案**不得**是任何一条受控码的文案——否则用户会以为自己知道问题出在哪
      expect(screen.queryByText(`${KEY}.${PREFIX}NotConfigured`)).not.toBeInTheDocument()
      // ⚠️ 这一行是**定向破坏补上的**（同 P2 的 F5-f）：只断言"通用文案出现了"的那条用例，
      // 分不出「正确接线 + 未知失败」与「压根接错了」——接错的函数返回 undefined，同样走 catch、
      // 同样落到通用文案。补上出站身份，G-i 才真的有判据。
      expect(mockPolish).toHaveBeenCalledTimes(1)
    },
    60_000,
  )

  it(
    'G-j 键契约：外壳与 P3 会渲染的 22 条后缀，在两份语言资源里都存在且非空',
    async () => {
      // 外壳 `AiTextGenerateButton` 里会出现的前缀无关后缀：12 条字面量 + `ERROR_SUFFIX` 的 7 个取值
      // （两个 403 出口共用 `Forbidden`）。⚠️ 这张清单是**手工维护**的：外壳里加一条字面量而忘了
      // 在语言文件里补键，本用例不会响。仍然值得留着——它是**唯一**看得住"两份语言文件同时缺这条键"
      // 的判据（`check-i18n.mjs` 只比对两份文件**之间**的键集合，两边一起缺是隐形的）。
      const shellSuffixes = [
        'Button',
        'ModalTitle',
        'Hint',
        'Aria',
        'Placeholder',
        'Generate',
        'Regenerate',
        'Generating',
        'Copy',
        'Copied',
        'Done',
        'Truncated',
        'NotConfigured',
        'Rejected',
        'UpstreamUnavailable',
        'RateLimited',
        'Forbidden',
        'InvalidInput',
        'Failed',
      ]
      // P3 独有的三条（模式选择器）：P1/P2 的请求体没有可选项，故这两组里没有它们
      const polishOnlySuffixes = ['ModeLabel', 'ModePolish', 'ModeSummarize']
      expect(shellSuffixes).toHaveLength(19)
      expect(polishOnlySuffixes).toHaveLength(3)

      const group = (res: unknown): Record<string, unknown> =>
        (res as { pages: { followUpTimeline: Record<string, unknown> } }).pages.followUpTimeline

      for (const suffix of [...shellSuffixes, ...polishOnlySuffixes]) {
        const key = `${PREFIX}${suffix}`
        // 空串也是缺键（`check-i18n.mjs` 把空值单独列出来，理由相同）：渲染出来是一片空白，
        // 比渲染出键名更难排查
        expect(group(zhCN)[key], `zh-CN 缺 ${KEY}.${key}`).toBeTruthy()
        expect(group(en)[key], `en 缺 ${KEY}.${key}`).toBeTruthy()
      }

      // 反向判据：P3 的三条独有键**不得**混进另外两组（混进去不会红任何门禁，只会让
      // "摘要按钮上出现一个没人用的模式选择词条"）
      const customerPage = (res: unknown): Record<string, unknown> =>
        (res as { pages: { customer: { detail: Record<string, unknown> } } }).pages.customer.detail
      for (const suffix of polishOnlySuffixes) {
        expect(customerPage(zhCN)).not.toHaveProperty(`${PREFIX}${suffix}`)
        expect(customerPage(en)).not.toHaveProperty(`${PREFIX}${suffix}`)
      }
    },
    60_000,
  )
})
