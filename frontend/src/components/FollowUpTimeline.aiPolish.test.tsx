import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import FollowUpTimeline from './FollowUpTimeline'
import { generateFollowUpPolish } from '../services/aiContentService'
import { createFollowUp, fetchFollowUps, updateFollowUp } from '../services/followUpService'
import { useAuthStore, type UserInfo } from '../store/authStore'
import { PERMS } from '../constants/permissions'

vi.mock('../services/aiContentService', () => ({
  generateFollowUpPolish: vi.fn(),
  generateEmailDraft: vi.fn(),
  generateCustomerSummary: vi.fn(),
}))
vi.mock('../services/followUpService', () => ({
  createFollowUp: vi.fn(),
  updateFollowUp: vi.fn(),
  fetchFollowUps: vi.fn(),
}))

/**
 * 104 · P3 的**宿主接线**（`FollowUpTimeline` 里那颗按钮）——外壳与 P3 组件各自的用例都看不见这一层。
 *
 * <p><b>为什么必须单独有一条</b>：`AiFollowUpPolishButton.test.tsx` 把 P3 组件当一个**受控**零件测
 * （原文由它传进去），故"原文从哪来、结果去哪"两件事在那边**恰好是被桩掉的那两面**。宿主这一层是纯粹的
 * 接线，而接线的错法（读错字段、回调没接、门开在别处）**每一条都能让那两个文件的全部用例保持绿色**——
 * 它们看不见宿主。这三条主张只在行为层可判：
 *
 * <ol>
 *   <li><b>W-a 权限门</b>：`ai:generate` 是**同一个**码管住三个能力（详见 `AiCustomerSummaryButton`
 *       的 ⚠️）。不持码的用户**看不到**这颗按钮（后端的 403 仍是兜底）。⚠️ 负向用例必须建立在
 *       **非 ADMIN** 用户上：ADMIN 在 `hasPerm` 里直通（`hooks/usePermission.ts:10`），
 *       拿它测"看不见"永远拿不到。
 *   <li><b>W-b 原文来自表单**当前**的值</b>：用户在编辑区写的那段字，就是出站的那段。宿主用
 *       `Form.useWatch` 读它——若把它换成"打开编辑时存一份快照"，用户在弹窗里继续改的字就送不出去，
 *       而那种错**不报任何错**，只让用户觉得"它没按我写的润色"。
 *   <li><b>W-c 结果写回表单，且不落库</b>：`onGenerated` 把模型返回的**原始**文本写进 content 字段
 *       （覆盖式，见宿主的 ②③ 注释）。本条同时钉住退路：写回**不等于**保存——`createFollowUp`
 *       一次都不能被调用。缺了这一条，"写回"与"顺手替你保存了"看起来一模一样。
 * </ol>
 *
 * <p>⚠️ 超时显式放到 60s（照本仓对重文件的既有做法）：本文件要渲染 `Timeline` + 两个 antd `Modal`
 * （其中一个是套在 `Form.Item.extra` 里的），默认 20s 在这台机器上会被并行 worker 抢到翻车。
 */
const CONTENT = '3 月 5 日与张经理通了电话，他说预算要等下一季度。'
const POLISHED = '整理后的跟进记录'
const PREFIX = 'aiPolish'

/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: [PERMS.aiGenerate] }

const mockPolish = vi.mocked(generateFollowUpPolish)
const mockCreate = vi.mocked(createFollowUp)

/** 跟进表单里那颗 P3 按钮（触发按钮；模态框里的"生成"是另一个名字）。 */
const polishTrigger = () => screen.queryByRole('button', { name: new RegExp(`${PREFIX}Button`) })

/** 打开"添加跟进"弹窗；等到表单真的渲染出来，后续的否定断言才不是假绿。 */
async function openFollowUpForm(): Promise<void> {
  renderWithProviders(<FollowUpTimeline customerId={1} />)
  await screen.findByRole('button', { name: /btnAdd/ })
  fireEvent.click(screen.getByRole('button', { name: /btnAdd/ }))
  await screen.findByRole('button', { name: /btnSave/ })
}

/**
 * 跟进原文那个编辑区。
 *
 * <p>它没有 `aria-label`（P3 的判据不该顺手改宿主的可访问名），故按"此刻页面上唯一的 `<textarea>`"
 * 取——此时 P3 的模态框还没开。**必须在这一刻取引用**：P3 模态框一开，页面上就有两个 textarea 了。
 */
function contentBox(): HTMLTextAreaElement {
  const boxes = document.querySelectorAll('textarea')
  expect(boxes).toHaveLength(1)
  return boxes[0] as HTMLTextAreaElement
}

describe('FollowUpTimeline 的 P3 接线（104 P3）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(fetchFollowUps).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50 } as never)
    mockPolish.mockResolvedValue({ text: POLISHED, model: 'claude-opus-5', truncated: false })
  })

  it(
    'W-a① 非 ADMIN 且不持 ai:generate ⇒ 表单里没有这颗按钮（同屏的其它控件仍在）',
    async () => {
      useAuthStore.setState({ user: salesNoPerm })
      await openFollowUpForm()

      expect(polishTrigger()).not.toBeInTheDocument()
      // 正对照：负向断言不得是"整张表单没渲染出来"的假绿
      expect(screen.getByRole('button', { name: /btnSave/ })).toBeInTheDocument()
      expect(contentBox()).toBeInTheDocument()
    },
    60_000,
  )

  it(
    'W-a② 持 ai:generate ⇒ 按钮出现，且它就在 content 字段旁边',
    async () => {
      useAuthStore.setState({ user: salesWithPerm })
      await openFollowUpForm()

      const trigger = polishTrigger()
      expect(trigger).toBeInTheDocument()
      // 同一个表单里没有第二颗（多渲染一处 = 同一段原文有两条出站路径）
      expect(screen.getAllByRole('button', { name: new RegExp(`${PREFIX}Button`) })).toHaveLength(1)
    },
    60_000,
  )

  it(
    'W-d 编辑既有记录、改过原文之后 ⇒ 送出去的是**改过**的那段，不是打开时的旧值',
    async () => {
      // ⚠️ 这一条才是"实时读表单"与"打开时存一份快照"的**唯一**分界：新建表单的原文从一开始就是空的，
      // 两种实现在那边长得一样（都只表现为"按钮从禁用到可用"）。编辑态下快照 = 旧值，
      // 而那种错**不报任何错**，只让用户觉得"它没按我写的润色"（宿主 `Form.useWatch` 的注释）。
      const OLD = '3 月 5 日与张经理通了电话。'
      const NEW = `${OLD} 他要求下周一前给报价。`
      vi.mocked(fetchFollowUps).mockResolvedValue({
        items: [
          {
            id: 5,
            customerId: 1,
            method: 'PHONE',
            content: OLD,
            followUpBy: 2, // === salesWithPerm.id，故"编辑"链接对他可见
            version: 0,
            createdAt: '2026-03-05T10:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 50,
      } as never)
      useAuthStore.setState({ user: salesWithPerm })
      renderWithProviders(<FollowUpTimeline customerId={1} />)

      // ⚠️ 列表**不是**挂载时拉的：宿主只在 `openCreate` 里 `void load()`（`FollowUpTimeline.tsx:70`，
      // 挂载路径上没有 `useEffect`）。故"编辑"链接要先经过"添加跟进"这一步才会出现——这是宿主的
      // 既存行为（不在本批改动范围内，我也没顺手改它），照它写就是了。
      fireEvent.click(await screen.findByRole('button', { name: /btnAdd/ }))
      fireEvent.click(await screen.findByText(/btnEdit/))
      await screen.findByRole('button', { name: /btnSave/ })

      // 打开编辑时表单已被 `openEdit` 填成 OLD ⇒ 按钮此刻就该是**可用**的（原文非空）。
      // 这一行同时是"填进去的值也走同一条读路径"的判据（换成快照实现时它倒是也会绿，
      // 所以下面那句"送出 NEW"才是本条的结论）。
      await waitFor(() => expect(polishTrigger()).toBeEnabled())

      fireEvent.change(contentBox(), { target: { value: NEW } })
      fireEvent.click(polishTrigger() as HTMLElement)
      fireEvent.click(await screen.findByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      await waitFor(() =>
        expect(mockPolish).toHaveBeenCalledWith({ content: NEW, mode: 'POLISH', customerId: 1 }),
      )
      // 反向：送出去的**不得**是打开时的旧值——逐字相等的断言已经排除它，这里点名写出来，
      // 免得后来者把它读成"随便一段非空内容"
      expect(mockPolish.mock.calls[0][0].content).not.toBe(OLD)
    },
    60_000,
  )

  it(
    'W-b/W-c 出站的是用户此刻写的那段；成功后写回表单、且不落库',
    async () => {
      useAuthStore.setState({ user: salesWithPerm })
      await openFollowUpForm()

      const box = contentBox()
      // 空表单时按钮是禁用的（"点了也白点"）——这是 W-b 的前置，顺带钉住
      expect(polishTrigger()).toBeDisabled()

      fireEvent.change(box, { target: { value: CONTENT } })
      await waitFor(() => expect(polishTrigger()).toBeEnabled())

      fireEvent.click(polishTrigger() as HTMLElement)
      fireEvent.click(await screen.findByRole('button', { name: new RegExp(`${PREFIX}Generate`) }))

      // W-b：出站的就是编辑区里那段（含日期），且归属客户由宿主给
      await waitFor(() =>
        expect(mockPolish).toHaveBeenCalledWith({
          content: CONTENT,
          mode: 'POLISH',
          customerId: 1,
        }),
      )

      // W-c：模型返回的原文被写回表单字段（覆盖式）……
      await waitFor(() => expect(box).toHaveValue(POLISHED))
      // ……**但没有任何记录落库**：写回只是把表单里那段字换掉，保存与否由用户点"保存"决定。
      // 缺了这一行，"写回"与"顺手替你存了一条跟进记录"在用例里长得一模一样。
      expect(mockCreate).not.toHaveBeenCalled()
      expect(updateFollowUp).not.toHaveBeenCalled()
    },
    60_000,
  )
})
