import AiTextGenerateButton from './AiTextGenerateButton'
import { generateCustomerSummary } from '../services/aiContentService'

/**
 * 「生成客户 360 摘要」按钮（104-ai-content-generation **P2**，宿主 = 客户详情页）。
 *
 * <p>与 P1 同外壳（{@link AiTextGenerateButton}）：四态、失败态保留编辑区、错误码 ⇒ 文案的总性都在那里。
 * 本文件只承担 **P2 自己那两件**：
 *
 * <ol>
 *   <li><b>请求体只有一个字段</b>：`customer-summary` 收 `{customerId}`。契约 §2.3 明写本能力
 *       **无** `tone` / `instruction`——请求体多一个自由文本字段，就等于多一条把任意用户输入送进
 *       提示词的路径（后端的 `CustomerSummaryRequest` 同样只有一个字段，两处是同一件事的两种表述）。
 *   <li><b>文案键前缀 `aiSummary`</b>：与 P1 的 `aiDraft` 是两组平行的键——两个能力在**同一屏**上，
 *       共用一组键会让「摘要按钮上写着邮件草稿的话」这种错**没有任何门禁看得见**。
 * </ol>
 *
 * <p>⚠️ <b>权限码与限流 scope 都与 P1 相同</b>（后端两个端点共用 `ai:generate` 与 `ai-generate`）：
 * 它们花的是同一笔外部计费调用、同一个日预算桶。故宿主用**同一个**权限判定门同时管住两个按钮。
 */
export interface AiCustomerSummaryButtonProps {
  /** 生成针对的客户（后端会先判数据范围：不归自己的客户 ⇒ 403 `FORBIDDEN`）。 */
  customerId: number
  /** 每次成功生成调用一次，参数是返回的**原始**文本。 */
  onGenerated?: (text: string) => void
}

export default function AiCustomerSummaryButton({
  customerId,
  onGenerated,
}: AiCustomerSummaryButtonProps) {
  return (
    <AiTextGenerateButton
      keyPrefix="aiSummary"
      generate={() => generateCustomerSummary({ customerId })}
      onGenerated={onGenerated}
    />
  )
}
