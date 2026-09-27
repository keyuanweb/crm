import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Radio, Space, Typography } from 'antd'
import AiTextGenerateButton from './AiTextGenerateButton'
import { generateFollowUpPolish } from '../services/aiContentService'
import type { FollowUpPolishMode, FollowUpPolishRequest } from '../types/aiContent'

/** 本能力的文案作用域：宿主 `FollowUpTimeline` 的那一组键（**与页面无关**，见外壳的 `AiKeyScope`）。 */
const KEY = 'pages.followUpTimeline'

/** 默认模式：**改动最小**的那一个（只整理措辞、不压缩内容）——同后端 `modeLabel` 的缺省取向。 */
const DEFAULT_MODE: FollowUpPolishMode = 'POLISH'

/** 与后端 `AiPromptCatalog.P3_CONTENT_MAX_CHARS` 同一个数（够长到能装下一次真实通话的记录，见该常量的理由）。 */
export const CONTENT_MAX_CHARS = 4000

/**
 * 「整理这段跟进记录」按钮（104-ai-content-generation **P3**，宿主 = `FollowUpTimeline` 的跟进表单）。
 *
 * <p>与 P1/P2 同外壳（{@link AiTextGenerateButton}）：四态、失败态保留编辑区、错误码 ⇒ 文案的总性
 * 都在那里。本文件只承担 **P3 自己那几件**：
 *
 * <ol>
 *   <li><b>请求体有三个字段、其中一个是可空的</b>：`followup-polish` 收 `{content, mode, customerId?}`。
 *       `customerId` **只在宿主给了的时候**才进请求体——照 P1 的纪律（`AiGenerateButton`），不恒带一个
 *       可能为空的键：后端的口径是"提供了就必须在可见范围内"，故带一个 `undefined` 的 id 与"不声明归属"
 *       是两件事，而 JSON 里两者都会被序列化成"没有这个键"这件事**不该靠巧合**。
 *   <li><b>原文来自宿主的表单，不由本组件持有</b>：用户边写边改的那段字在 `FollowUpTimeline` 的
 *       `Form` 里（内存中的唯一一份）。本组件只读它的当前值去出站、并把结果**交回**宿主（`onGenerated`），
 *       由宿主写回表单——这样"用户手改过的草稿"始终只有一处落脚，本组件不留第二份。
 *   <li><b>模式选择器住在 `extraControls` 插槽里</b>：`mode` 是请求体的一部分，故它的状态在这里、
 *       外壳看不见它（照外壳"看不见请求体"的纪律）。默认 `POLISH`（改动最小的那个模式）。
 *   <li><b>没有原文时按钮禁用</b>：这不是入参校验的替身（长度上限仍由后端判），而是"点了也白点"——
 *       空输入必然回 400，而那个 400 的文案（`BadRequest` ⇒ "输入不合法"）对用户来说是句废话：
 *       他手里的编辑区本来就是空的。
 * </ol>
 *
 * <p>⚠️ <b>文案键作用域是 `pages.followUpTimeline` 而不是 `pages.customer.detail`</b>：宿主的这个
 * 组件同时挂在客户详情页与线索详情页上。把 P3 的键塞进客户页那一组，会让线索页上的按钮读客户页的词条，
 * 而两份语言文件都有那些键、`check-i18n.mjs` 只比对两份文件之间的键集合——用错作用域在那套门禁下
 * **是隐形的**（外壳的 `AiKeyScope` 联合类型是唯一的拦路者）。
 */
export interface AiFollowUpPolishButtonProps {
  /**
   * 待整理的原文（宿主表单里**当前**的值）。
   *
   * <p>⚠️ 判空用 `trim()`：只含空格/换行的"跟进内容"在后端同样被拒（`AiFollowUpPolishService` 的
   * 空白串与 null 同判），前端这里用同一个口径，免得用户点了一个必然失败的按钮。
   */
  content: string
  /** 可选：跟进所属客户（后端判可见性：不归自己的客户 ⇒ 403 `FORBIDDEN`）。线索页上不传。 */
  customerId?: number
  /** 每次成功生成调用一次，参数是返回的**原始**文本（宿主把它写回表单字段）。 */
  onGenerated?: (text: string) => void
}

export default function AiFollowUpPolishButton({
  content,
  customerId,
  onGenerated,
}: AiFollowUpPolishButtonProps) {
  const { t } = useTranslation()
  const [mode, setMode] = useState<FollowUpPolishMode>(DEFAULT_MODE)

  const selector = (
    <Space direction="vertical" size={0} style={{ marginTop: 4 }}>
      <Typography.Text type="secondary">{t(`${KEY}.aiPolishModeLabel`)}</Typography.Text>
      <Radio.Group
        value={mode}
        onChange={(e) => setMode(e.target.value as FollowUpPolishMode)}
        options={[
          { value: 'POLISH', label: t(`${KEY}.aiPolishModePolish`) },
          { value: 'SUMMARIZE', label: t(`${KEY}.aiPolishModeSummarize`) },
        ]}
      />
    </Space>
  )

  return (
    <AiTextGenerateButton
      keyScope={KEY}
      keyPrefix="aiPolish"
      disabled={content.trim() === ''}
      generate={() => {
        const request: FollowUpPolishRequest =
          customerId === undefined ? { content, mode } : { content, mode, customerId }
        return generateFollowUpPolish(request)
      }}
      onGenerated={onGenerated}
      extraControls={selector}
    />
  )
}
