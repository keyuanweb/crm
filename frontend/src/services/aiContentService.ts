import { apiClient } from './apiClient'
import type {
  CustomerSummary,
  CustomerSummaryRequest,
  EmailDraft,
  EmailDraftRequest,
} from '../types/aiContent'

/**
 * 生成邮件草稿：`POST /api/v1/ai/email-draft`（104-ai-content-generation P1）。
 *
 * <p>契约：权限码 `ai:generate`（预置角色**零授予**，见 `AiPermissionGrantIT`）；服务端另有突发
 * 限流（10 次 / 60 秒）与**每用户每日 token 预算**（耗尽 ⇒ 429 + `Retry-After`）。受控错误码的
 * 取值集合在 `types/aiContent.ts`，文案映射在 `components/AiGenerateButton.tsx`。
 *
 * <p>⚠️ <b>显式 `timeout: 0`（退出全局 30s）</b>：服务端单次生成的等待上限是它自己的
 * `crm.ai.timeout-seconds`（默认 60s，FR-008——"服务端超时由此控制，不受前端全局 30s 约束"）。
 * 模型出字可能比 30s 久，沿用全局超时会把"服务端其实成功了"报成失败：用户看到超时、重试，
 * 而每一次重试都已在上游花了钱。判例与各处**导出件**相同（见 `services/apiClient.ts` 对
 * `timeout: 0` 的说明），代价同样是调用方要自己承受等待——这里由模态框的进行中态承担。
 */
export async function generateEmailDraft(request: EmailDraftRequest): Promise<EmailDraft> {
  const { data } = await apiClient.post('/ai/email-draft', request, { timeout: 0 })
  return data.data as EmailDraft
}

/**
 * 生成客户 360 摘要：`POST /api/v1/ai/customer-summary`（104-ai-content-generation P2）。
 *
 * <p>契约、权限码、限流与日预算与 {@link generateEmailDraft} **逐条相同**（后端两个端点共用
 * `ai:generate` / `ai-generate` 与同一个日预算桶）——包括下面那条 `timeout: 0`：
 *
 * <p>⚠️ <b>显式 `timeout: 0`（退出全局 30s）</b>：理由与 P1 同源（服务端自己的等待上限是
 * `crm.ai.timeout-seconds`，默认 60s）。这里再写一遍不是因为理由不同，而是因为**它是每个调用点
 * 各自的责任**：`apiClient` 的全局超时是 30s，漏掉这一行的那个端点会以"服务端其实成功了"的形式
 * 报失败（用户重试 ⇒ 每次都已在模型侧花了钱）。两个调用点都要有，且都由各自的用例看着。
 */
export async function generateCustomerSummary(
  request: CustomerSummaryRequest,
): Promise<CustomerSummary> {
  const { data } = await apiClient.post('/ai/customer-summary', request, { timeout: 0 })
  return data.data as CustomerSummary
}
