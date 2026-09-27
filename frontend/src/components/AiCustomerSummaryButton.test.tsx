import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import AiCustomerSummaryButton from './AiCustomerSummaryButton'
import { generateCustomerSummary, generateEmailDraft } from '../services/aiContentService'
import type { CustomerSummary } from '../types/aiContent'

vi.mock('../services/aiContentService', () => ({
  generateCustomerSummary: vi.fn(),
  generateEmailDraft: vi.fn(),
}))

/**
 * 104 · `AiCustomerSummaryButton`（P2 客户 360 摘要）——**只钉 P2 自己那两件**。
 *
 * <p><b>为什么这里不重复 F1–F4</b>：四态、失败态保留编辑区、错误码 ⇒ 文案的分派全在共用外壳
 * `AiTextGenerateButton` 里，且已由 `AiGenerateButton.test.tsx` 逐条看着。此处再抄一遍只会得到
 * 两份会各自漂移的表述。本文件的判据是**外壳看不见的那一面**：
 *
 * <ol>
 *   <li><b>F5-a 出站调用搭对了没有</b>：调的是 `generateCustomerSummary`、**且**请求体恰为
 *       `{customerId}`（不是 `{customerId, opportunityId}`、也不是多带了 `tone`/`instruction`），
 *       **且** `generateEmailDraft` 一次都没被调用。这是本组件最容易被接错的一处——外壳**看不见 id**
 *       （它只拿到一个闭包），所以"两个能力的请求体被混在一起"这件事在类型上完全合法。
 *   <li><b>F5-b 文案键前缀是 `aiSummary` 而不是 `aiDraft`</b>：两个能力在**同一屏**上
 *       （`CustomerDetailPage.tsx`），共用一组键会让「摘要按钮上写着邮件草稿的话」没有任何门禁看得见
 *       ——`check-i18n.mjs` 只比对两份语言文件之间的键集合，不扫源码用法；而测试把 `t` 桩成恒等函数，
 *       两边一起错就一起绿。故这里断言的是**键名**，不是"某句话出现了"。
 * </ol>
 *
 * <p>⚠️ 超时显式放到 60s（照本仓对重文件的既有做法）：每条用例都要渲染一次 antd `Modal`
 * （内含 `Input.TextArea`），默认 20s 在这台机器上会被并行 worker 抢到翻车，而那是**假的失败**。
 */
const KEY = 'pages.customer.detail'
const PREFIX = 'aiSummary'
const PLACEHOLDER = `${KEY}.${PREFIX}Placeholder`

const mockSummary = vi.mocked(generateCustomerSummary)
const mockDraft = vi.mocked(generateEmailDraft)

function summary(overrides: Partial<CustomerSummary> = {}): CustomerSummary {
  return { text: '生成的客户摘要', model: 'claude-opus-5', truncated: false, ...overrides }
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
async function openModal(onGenerated?: (text: string) => void): Promise<HTMLElement> {
  renderWithProviders(<AiCustomerSummaryButton customerId={7} onGenerated={onGenerated} />)
  fireEvent.click(screen.getByRole('button', { name: /aiSummaryButton/ }))
  return screen.findByPlaceholderText(PLACEHOLDER)
}

describe('AiCustomerSummaryButton（104 P2 客户 360 摘要）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it(
    'F5-a 成功 ⇒ 调的是 customer-summary、请求体恰为 {customerId}、且不碰 email-draft',
    async () => {
      mockSummary.mockResolvedValue(summary())
      const onGenerated = vi.fn()
      const box = await openModal(onGenerated)

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      // ⚠️ 顺序是被实测出来的（照 P1 的 D5-b 那条教训）：**点名缺陷的断言必须排在状态断言之前**。
      // 本条初版把 `waitFor(box).toHaveValue(...)` 放在最前，定向破坏 E7（把出站换成
      // `generateEmailDraft`）时红的正是那一行——而"接错了端点"这件事，`toHaveBeenCalledWith`
      // 才是能说出它名字的那一条；它当时排在后面，**根本没被执行到**。
      // 这条既是等待、又是**点名缺陷**的那一条：接错函数 ⇒ "Number of calls: 0"；
      // 请求体多/少字段 ⇒ 逐字相等失败并打出实际入参。
      await waitFor(() => expect(mockSummary).toHaveBeenCalledWith({ customerId: 7 }))
      expect(mockSummary).toHaveBeenCalledTimes(1)
      // 反向判据：接错端点在类型上完全合法（两个服务函数的入参都能接受 `{customerId}`，
      // `EmailDraftRequest` 的另两个字段都是可选的），只有这一行看得住
      expect(mockDraft).not.toHaveBeenCalled()
      // 宿主契约：每次成功恰好回调一次，参数是返回的**原始**文本
      await waitFor(() => expect(onGenerated).toHaveBeenCalledTimes(1))
      expect(onGenerated).toHaveBeenCalledWith('生成的客户摘要')
      expect(box).toHaveValue('生成的客户摘要')
    },
    60_000,
  )

  it(
    'F5-b 未配置（409）⇒ 用的是 P2 那组键（aiSummary*），不是 P1 的 aiDraft*',
    async () => {
      mockSummary.mockRejectedValue(apiError('AI_NOT_CONFIGURED', 409))
      await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiSummaryNotConfigured`)).toBeInTheDocument()
      // 同屏两个能力共用一组键的后果：摘要按钮上写着邮件草稿的话，而没有任何别的门禁看得见
      expect(screen.queryByText(`${KEY}.aiDraftNotConfigured`)).not.toBeInTheDocument()
    },
    60_000,
  )

  it(
    'F5-c 数据范围拒（403 FORBIDDEN）⇒ P2 的越权文案（与 022/权限切面同一屏也不混淆）',
    async () => {
      mockSummary.mockRejectedValue(apiError('FORBIDDEN', 403))
      await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiSummaryForbidden`)).toBeInTheDocument()
    },
    60_000,
  )

  it(
    'F5-d 截断 ⇒ P2 的截断提示，且不得同时呈现为"生成完成"',
    async () => {
      mockSummary.mockResolvedValue(summary({ text: '半截的摘要', truncated: true }))
      const box = await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiSummaryTruncated`)).toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.aiSummaryDone`)).not.toBeInTheDocument()
      // 半截文本仍要落在编辑区（用户得拿它续写/手改），但上面两条才是结论：它是"未完成"的
      await waitFor(() => expect(box).toHaveValue('半截的摘要'))
    },
    60_000,
  )

  it(
    'F5-e 正对照：未截断 ⇒ 呈现"生成完成"，截断提示不出现',
    async () => {
      mockSummary.mockResolvedValue(summary())
      await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiSummaryDone`)).toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.aiSummaryTruncated`)).not.toBeInTheDocument()
    },
    60_000,
  )

  it(
    'F5-f 无码的失败 ⇒ 走 P2 的通用文案，不冒充某一个具体的码',
    async () => {
      // 非 axios 错误（超时/断网/CORS 都长这样）：extractErrorCode 返回 undefined，
      // extractErrorMessage 对非 axios 错误**直接返回 fallback**（apiClient.ts:94），
      // 即组件传进去的 t(k('Failed'))——测试里 t 是恒等函数，故断言的是键名。
      mockSummary.mockRejectedValue(new Error('Network Error'))
      await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiSummaryGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiSummaryFailed`)).toBeInTheDocument()
      // 通用文案**不得**是任何一条受控码的文案——否则用户会以为自己知道问题出在哪
      expect(screen.queryByText(`${KEY}.aiSummaryNotConfigured`)).not.toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.aiSummaryForbidden`)).not.toBeInTheDocument()
      // ⚠️ 这一行是**定向破坏 E7 补上的**：本条原本只有上面三句，而 E7（把出站换成
      // `generateEmailDraft`）下它**依然全绿**——接错的函数返回 `undefined`，同样走 catch、
      // 同样落到通用文案。⇒ 只断言"通用文案出现了"的那条用例，分不出「正确接线 + 未知失败」
      // 与「压根接错了」。补上出站身份，F5-f 才真的有判据。
      expect(mockSummary).toHaveBeenCalledTimes(1)
    },
    60_000,
  )
})
