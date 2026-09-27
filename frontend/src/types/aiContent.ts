/**
 * AI 文本生成（104-ai-content-generation）的前端契约类型。
 *
 * <p><b>为什么能力名与错误码要写成联合类型，而不是随手用 `string`</b>：这两处都是"加了一个新成员、
 * 却忘了在界面上给它一个分支"的经典藏身处。能力名决定提示词往哪走，错误码决定用户看到哪句话——
 * 前者写错是**功能错**，后者写错是**用户卡在一个没有出路的提示上**（后端 `ErrorCode` 里的字符串是
 * 接口契约的一部分，改它要动契约）。收成联合类型之后，"漏了一个分支"在**编译期**就报错，
 * 而不是等到某个用户碰上那个错误码才发现界面上是空白。
 *
 * <p>本文件只放**契约**（后端请求/响应的形状 + 受控码的取值集合）；"哪个码配哪句话"是界面决定，
 * 放在组件里（`components/AiGenerateButton.tsx` 的 `MESSAGE_KEY`）。
 */

/** 四种能力的名字（与后端 `AiContentService.AiRequest.capability` 同形；P1 只用到第一种）。 */
export type AiCapability = 'email-draft' | 'customer-summary' | 'followup-polish' | 'opportunity-advice'

/**
 * 受控错误码的**取值集合**（与后端 `ErrorCode` 逐字一致）。
 *
 * <p>用数组而不是直接写联合字面量，是为了让 {@link asAiErrorCode} 能真的**判定成员资格**——
 * 若只写 `type AiErrorCode = 'A' | 'B'`，运行时就没有任何东西能回答"这个字符串是不是已知码"，
 * 于是那个收敛函数只能靠断言（`.includes(x as AiErrorCode)` 里的 `as` 就把类型系统关掉了）。
 */
export const AI_ERROR_CODES = [
  /** 未配置（409）：出站前的配置门，零出站、零记录。 */
  'AI_NOT_CONFIGURED',
  /** 上游拒绝（422）：同一输入重试通常同样失败，故与"上游不可用"分开。 */
  'AI_GENERATION_REJECTED',
  /** 上游不可用（503）：连接失败、上游 429/5xx——过一会儿重试有意义。 */
  'AI_UPSTREAM_UNAVAILABLE',
  /** 突发限流或**日预算耗尽**（429）：后者带 `Retry-After`（到本地零点还有多少秒）。 */
  'RATE_LIMITED',
  /** 权限切面拒（403)：`ai:generate` 没授出去。 */
  'PERMISSION_DENIED',
  /** 数据范围拒（403）：不归自己的客户。本仓有**两个** 403 出口，见 tasks.md 的 ⚠️。 */
  'FORBIDDEN',
] as const

export type AiErrorCode = (typeof AI_ERROR_CODES)[number]

/** 把任意字符串（`extractErrorCode` 的结果）收敛为已知受控码；未知/缺失 ⇒ `undefined`（走通用文案）。 */
export function asAiErrorCode(code: string | undefined): AiErrorCode | undefined {
  return code !== undefined && (AI_ERROR_CODES as readonly string[]).includes(code)
    ? (code as AiErrorCode)
    : undefined
}

/** 邮件草稿的语气（与后端 `AiPromptCatalog.TONES` 逐字一致；不传 ⇒ 后端取 `FORMAL`）。 */
export type EmailDraftTone = 'FORMAL' | 'FRIENDLY' | 'CONCISE'

/** `POST /api/v1/ai/email-draft` 的请求体（= 后端 `EmailDraftRequest` record）。 */
export interface EmailDraftRequest {
  customerId: number
  /** 可空：草稿不绑定商机时后端只用客户资料。 */
  opportunityId?: number
  tone?: EmailDraftTone
  /** 附加指示；后端上限 4000 字符（超长回 400，且**在出站之前**）。 */
  instruction?: string
}

/**
 * 全部四个能力的成功响应体**同形**（后端也是四个 `*Response` record，字段逐字相同）：
 * P1 `EmailDraftResponse` / P2 `CustomerSummaryResponse` / P3 / P4 都是 `{text, model, truncated}`。
 *
 * <p>故这里只有**一个**形状，各能力用别名指过来（`EmailDraft` / `CustomerSummary`）。写四份同形
 * 的 interface 不会更安全，只会让"四个能力里有一个多了/少了一个字段"变成看不出的事。
 */
export interface AiGenerationResult {
  text: string
  /** 实际出字的模型名（部署可换模型，故由响应带回而不是前端写死）。 */
  model: string
  /**
   * 达到 `max_tokens` 而截断。
   *
   * <p>⚠️ 截断**不是错误**（仍是 200）：界面必须把它呈现为"未完成"态，**不得**把半截文本当完整
   * 结果给用户（契约 §2.5 / F4）。
   */
  truncated: boolean
}

/** `POST /api/v1/ai/email-draft` 的成功响应体（= 后端 `EmailDraftResponse` record）。 */
export type EmailDraft = AiGenerationResult

/** `POST /api/v1/ai/customer-summary` 的成功响应体（= 后端 `CustomerSummaryResponse` record）。 */
export type CustomerSummary = AiGenerationResult

/**
 * `POST /api/v1/ai/customer-summary` 的请求体（= 后端 `CustomerSummaryRequest` record）。
 *
 * <p><b>只有一个字段</b>，且这不是"先留空、以后再补"：契约 §2.3 明写本能力无 `tone` / `instruction`。
 * 请求体多一个自由文本字段，就等于多一条把任意用户输入送进提示词的路径——而这条路径上的
 * 防护（长度上限、内容比例）在 P1 是**单独定过**的，搬过来并不免费。
 */
export interface CustomerSummaryRequest {
  customerId: number
}
