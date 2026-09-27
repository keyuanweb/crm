import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import AiGenerateButton from './AiGenerateButton'
import { generateEmailDraft } from '../services/aiContentService'
import type { EmailDraft } from '../types/aiContent'

vi.mock('../services/aiContentService', () => ({ generateEmailDraft: vi.fn() }))

/**
 * 104 · `AiGenerateButton` 的四态（plan.md 的 F1–F4，宿主裁决 A = 客户详情页 + 复制到剪贴板）。
 *
 * <p><b>为什么这一面必须有用例</b>：后端把 409/422/429/503 分得再清楚，界面上如果一律弹一句
 * "生成失败"，用户就分不清"这个部署没开 AI"（重试一万次也没用）与"刚好多点了两次"（等一分钟就好）。
 * 而这一面**没有任何后端用例看着**——它全在浏览器里。
 *
 * <p><b>四条断言各对应一处真实改动</b>：
 * <ol>
 *   <li><b>F1 未配置</b>：409 `AI_NOT_CONFIGURED` ⇒ 失败文案，**且编辑区保留用户已输入的内容**。
 *       后半句是重点：本组件没有任何持久化（P1 零新增表，草稿不落库，FR-019 已订正为"展示 + 复制"），
 *       编辑区里的字是**内存里的唯一一份**——失败时清空它就是数据丢失。这条正是定向破坏 D10 的靶子。
 *   <li><b>F2 生成中</b>：按钮 `disabled` + 进行中提示；重复点击**不得**发起第二次调用。
 *       一次模型调用是要花钱的（并计入日预算），"连点两下点出两次生成"在界面上完全看不出来。
 *   <li><b>F3 成功</b>：结果写进编辑区，`onGenerated` **恰好一次**、参数是返回文本（P2–P4 的宿主
 *       要依赖这个契约；P1 的宿主不用它，见组件注释）。
 *   <li><b>F4 截断</b>：`truncated` ⇒ 截断提示，**且不得**同时给出"生成完成"（截断是 200，
 *       后端不会替界面决定它是成功还是未完成；把半截文本当完整结果给用户，是这个能力最容易犯的错）。
 *   <li><b>F4-b 正对照</b>：未截断 ⇒ "生成完成"出现、截断提示不出现。两条一起才排除"永远警告"
 *       或"永远完成"这种两头都能骗过单侧的假实现。
 *   <li><b>补充（不在 F1–F4 内）</b>：复制把**编辑区当前**文本写进剪贴板（含用户手改后的内容）——
 *       "展示 + 复制"里"复制"那一半，没有任何别的用例看着它。
 * </ol>
 *
 * <p>⚠️ 超时显式放到 60s（照本仓对重文件的既有做法）：本文件的每条用例都要渲染一次 antd `Modal`
 * （内含 `Input.TextArea`），默认 20s 在这台机器上会被并行 worker 抢到翻车，而那是**假的失败**。
 */
const KEY = 'pages.customer.detail'
const PLACEHOLDER = `${KEY}.aiDraftPlaceholder`

const mockGenerate = vi.mocked(generateEmailDraft)

function draft(overrides: Partial<EmailDraft> = {}): EmailDraft {
  return { text: '生成的草稿正文', model: 'claude-opus-5', truncated: false, ...overrides }
}

/**
 * 一个后端受控错误。
 *
 * <p>⚠️ **必须带 `isAxiosError: true`**：`extractErrorCode` 走 `axios.isAxiosError`，缺这个标记时
 * 它会返回 `undefined`，于是所有按码分派的断言都会落到通用文案上——用例会以"文案不匹配"红掉，
 * 指向的却是桩的形状，而不是被测代码。
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
  renderWithProviders(<AiGenerateButton customerId={7} opportunityId={9} onGenerated={onGenerated} />)
  // 图标也参与可访问名 ⇒ 用正则做部分匹配（照本仓既有用例的写法）
  fireEvent.click(screen.getByRole('button', { name: /aiDraftButton/ }))
  return screen.findByPlaceholderText(PLACEHOLDER)
}

describe('AiGenerateButton（104 P1 邮件草稿的四态）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it(
    'F1 未配置（409 AI_NOT_CONFIGURED）⇒ 失败文案，且编辑区保留用户已输入的内容',
    async () => {
      mockGenerate.mockRejectedValue(apiError('AI_NOT_CONFIGURED', 409))
      const box = await openModal()
      fireEvent.change(box, { target: { value: '用户手打的内容' } })

      fireEvent.click(screen.getByRole('button', { name: /aiDraftGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiDraftNotConfigured`)).toBeInTheDocument()
      // D10 的护栏：失败态若清空编辑区，这一行转红（本组件没有别的地方保存过它）
      expect(box).toHaveValue('用户手打的内容')
    },
    60_000,
  )

  it(
    'F2 生成中 ⇒ 按钮 disabled + 进行中提示；重复点击不得发起第二次',
    async () => {
      let resolveIt!: (value: EmailDraft) => void
      mockGenerate.mockReturnValue(
        new Promise<EmailDraft>((resolve) => {
          resolveIt = resolve
        }),
      )
      await openModal()
      const button = screen.getByRole('button', { name: /aiDraftGenerate/ })

      fireEvent.click(button)
      expect(await screen.findByText(`${KEY}.aiDraftGenerating`)).toBeInTheDocument()
      await waitFor(() => expect(button).toBeDisabled())

      // 第二次点击：无论事件是否真的到达处理器，都不得再发一次出站请求
      fireEvent.click(button)
      expect(mockGenerate).toHaveBeenCalledTimes(1)

      resolveIt(draft())
      await waitFor(() => expect(screen.queryByText(`${KEY}.aiDraftGenerating`)).not.toBeInTheDocument())
    },
    60_000,
  )

  it(
    'F3 成功 ⇒ 结果写入编辑区，onGenerated 恰好一次且参数为返回文本',
    async () => {
      mockGenerate.mockResolvedValue(draft())
      const onGenerated = vi.fn()
      const box = await openModal(onGenerated)

      fireEvent.click(screen.getByRole('button', { name: /aiDraftGenerate/ }))

      await waitFor(() => expect(box).toHaveValue('生成的草稿正文'))
      expect(mockGenerate).toHaveBeenCalledTimes(1)
      // 请求体的形状：两个 id 都带上了（otherwise 生成的是"某个客户"的草稿，而没人看得出来）
      expect(mockGenerate).toHaveBeenCalledWith({ customerId: 7, opportunityId: 9 })
      expect(onGenerated).toHaveBeenCalledTimes(1)
      expect(onGenerated).toHaveBeenCalledWith('生成的草稿正文')
    },
    60_000,
  )

  it(
    'F4 截断 ⇒ 展示截断提示，且不得同时呈现为"生成完成"',
    async () => {
      mockGenerate.mockResolvedValue(draft({ text: '半截的正文', truncated: true }))
      const box = await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiDraftGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiDraftTruncated`)).toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.aiDraftDone`)).not.toBeInTheDocument()
      // 半截文本仍要落在编辑区（用户得拿它续写/手改），但上面两条才是结论：它是"未完成"的
      await waitFor(() => expect(box).toHaveValue('半截的正文'))
    },
    60_000,
  )

  it(
    'F4-b 正对照：未截断 ⇒ 呈现"生成完成"，且不出现截断提示',
    async () => {
      mockGenerate.mockResolvedValue(draft())
      await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiDraftGenerate/ }))

      expect(await screen.findByText(`${KEY}.aiDraftDone`)).toBeInTheDocument()
      expect(screen.queryByText(`${KEY}.aiDraftTruncated`)).not.toBeInTheDocument()
    },
    60_000,
  )

  it(
    '补充（F1–F4 之外）：复制把编辑区当前文本写进剪贴板（含用户手改后的内容）',
    async () => {
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
      mockGenerate.mockResolvedValue(draft())
      const box = await openModal()

      fireEvent.click(screen.getByRole('button', { name: /aiDraftGenerate/ }))
      await waitFor(() => expect(box).toHaveValue('生成的草稿正文'))

      // 复制的是**改过之后**的文本，不是刚生成的那一版——"一键复制"必须对得上眼前的编辑区
      fireEvent.change(box, { target: { value: '手改后的正文' } })
      fireEvent.click(screen.getByRole('button', { name: /aiDraftCopy/ }))

      await waitFor(() => expect(writeText).toHaveBeenCalledWith('手改后的正文'))
    },
    60_000,
  )
})
