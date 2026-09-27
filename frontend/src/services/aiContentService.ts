import { apiClient } from './apiClient'
import type { EmailDraft, EmailDraftRequest } from '../types/aiContent'

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
