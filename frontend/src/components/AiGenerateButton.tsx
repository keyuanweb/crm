import AiTextGenerateButton from './AiTextGenerateButton'
import { generateEmailDraft } from '../services/aiContentService'
import type { EmailDraftRequest } from '../types/aiContent'

/**
 * 「生成邮件草稿」按钮（104-ai-content-generation **P1**，宿主 = 客户详情页）。
 *
 * <p>四态、失败态保留编辑区、错误码 ⇒ 文案的总性、与 022 智能建议的视觉区分，这四条都在共用外壳
 * {@link AiTextGenerateButton} 里（含理由），本文件只承担 **P1 自己那两件**：
 *
 * <ol>
 *   <li><b>请求体的形状</b>：`email-draft` 收 `{customerId, opportunityId?}`——`opportunityId`
 *       **不传**时后端只按客户资料写（而不是带一个空商机），故这里按 `undefined` 与否分两种字面量，
 *       而不是恒带上一个可能为空的键。
 *   <li><b>文案键前缀 `aiDraft`</b>：与 P2 的 `aiSummary` 是两组平行的键。
 * </ol>
 *
 * <p><b>不拿 `onGenerated` 做插入</b>：宿主裁决 A 之下 P1 的形态是「展示 + 复制」——详情页没有
 * 可插入的编辑区，故宿主**不传**本回调；它的契约由外壳的 F3 钉住，供 P2–P4 有落点的宿主使用
 * （每次**成功**生成调用一次，参数是原始返回文本，不含用户后续的手改）。
 */
export interface AiGenerateButtonProps {
  /** 生成针对的客户（后端会先做数据范围判定：不归自己的客户 ⇒ 403 `FORBIDDEN`）。 */
  customerId: number
  /** 可选：绑定到某个商机。 */
  opportunityId?: number
  /** 每次成功生成调用一次，参数是返回的**原始**文本（宿主自行决定拿它做什么）。 */
  onGenerated?: (text: string) => void
}

export default function AiGenerateButton({
  customerId,
  opportunityId,
  onGenerated,
}: AiGenerateButtonProps) {
  return (
    <AiTextGenerateButton
      keyPrefix="aiDraft"
      generate={() => {
        const request: EmailDraftRequest =
          opportunityId === undefined ? { customerId } : { customerId, opportunityId }
        return generateEmailDraft(request)
      }}
      onGenerated={onGenerated}
    />
  )
}
