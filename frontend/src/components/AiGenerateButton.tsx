import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, App, Button, Input, Modal, Space, Typography } from 'antd'
import { CopyOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { extractErrorCode, extractErrorMessage } from '../services/apiClient'
import { generateEmailDraft } from '../services/aiContentService'
import { asAiErrorCode, type AiErrorCode, type EmailDraftRequest } from '../types/aiContent'

/**
 * 「生成邮件草稿」按钮 + 草稿模态框（104-ai-content-generation P1，宿主 = 客户详情页）。
 *
 * <p><b>四态</b>（plan.md 的 F1–F4）：未配置（后端 409 `AI_NOT_CONFIGURED`）/ 生成中 / 成功 /
 * 截断。四态都由 {@link Status} 一个状态位承担——分成几个布尔量就会出现"生成中且成功"这类
 * 没有意义的组合，而界面必须对每一种组合都给出一个（正确的）画面。
 *
 * <p><b>为什么失败态不清空编辑区</b>：用户可能在上一版草稿上手改过一段，一次失败的重新生成
 * 不该把那段抹掉。这不是体贴，是**数据丢失**：本组件没有任何持久化（P1 零新增表，草稿不落库，
 * 见 spec.md FR-019 的 ✅ 订正），编辑区里的字是内存里的唯一一份。故 catch 里只改状态位，
 * **不碰 `draft`**。
 *
 * <p><b>为什么错误文案按码分派而不是直接显示后端 message</b>：两个 403 出口（权限切面
 * `PERMISSION_DENIED` 与数据范围 `FORBIDDEN`）与 429 的两种成因（突发限流 / 日预算耗尽）
 * 对用户是**不同的下一步**；后端的中文文案是给人看的兜底，不是给分支用的判据。映射写成
 * `Record<AiErrorCode, string>`，于是**加了一个码却忘了它的文案 = 编译期报错**（而非界面上多一片空白）。
 *
 * <p><b>不拿 `onGenerated` 做插入</b>：宿主裁决 A 之下 P1 的形态是「展示 + 复制」——详情页没有
 * 可插入的编辑区，故宿主**不传**本回调；它的契约由 `AiGenerateButton.test.tsx` 的 F3 钉住，
 * 供 P2–P4 有落点的宿主使用（每次**成功**生成调用一次，参数是原始返回文本，不含用户后续的手改）。
 *
 * <p>⚠️ <b>视觉必须与 022 的智能建议区分</b>：022 的约定是琥珀金 `#faad14` + `BulbOutlined` +
 * `ai-icon-pulse` 脉冲（`DashboardPage.tsx` / `index.css`）。本组件用的是**闪电图标 + antd 语义色
 * 的原生 Alert/Button**，没有任何自定义色值与动画——两个"AI"在同一屏里不会互相冒充（`check-ui.mjs`
 * R1 也禁止组件里出现品牌色字面量）。
 */
const KEY = 'pages.customer.detail'

/** 受控码 ⇒ 文案键。`Record<AiErrorCode, string>` 的总性就是"缺分支在编译期被挡"。 */
const MESSAGE_KEY: Record<AiErrorCode, string> = {
  AI_NOT_CONFIGURED: `${KEY}.aiDraftNotConfigured`,
  AI_GENERATION_REJECTED: `${KEY}.aiDraftRejected`,
  AI_UPSTREAM_UNAVAILABLE: `${KEY}.aiDraftUpstreamUnavailable`,
  RATE_LIMITED: `${KEY}.aiDraftRateLimited`,
  PERMISSION_DENIED: `${KEY}.aiDraftForbidden`,
  FORBIDDEN: `${KEY}.aiDraftForbidden`,
}

/** 未配置 / 生成中 / 成功 / 截断 / 失败——四态外加一个"还没点过"的初始态。 */
type Status = 'idle' | 'generating' | 'done' | 'truncated' | 'error'

export interface AiGenerateButtonProps {
  /** 生成针对的客户（后端会先做数据范围判定：不归自己的客户 ⇒ 403 `FORBIDDEN`）。 */
  customerId: number
  /** 可选：绑定到某个商机。 */
  opportunityId?: number
  /** 每次成功生成调用一次，参数是返回的**原始**文本（宿主自行决定拿它做什么）。 */
  onGenerated?: (text: string) => void
}

export default function AiGenerateButton({ customerId, opportunityId, onGenerated }: AiGenerateButtonProps) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  /** 是否成功生成过（只影响按钮文案：生成 / 重新生成）——与"草稿非空"不是同一件事，见 F1。 */
  const [generatedOnce, setGeneratedOnce] = useState(false)
  const [errorKey, setErrorKey] = useState<string | null>(null)
  const [errorText, setErrorText] = useState('')

  const generate = async () => {
    // 双保险：按钮此时是 disabled，但"点击"这件事在某些环境里照样会到达处理器（F2 的判据就是它）。
    if (status === 'generating') return
    setStatus('generating')
    setErrorKey(null)
    setErrorText('')
    const request: EmailDraftRequest =
      opportunityId === undefined ? { customerId } : { customerId, opportunityId }
    try {
      const result = await generateEmailDraft(request)
      setDraft(result.text)
      setGeneratedOnce(true)
      setStatus(result.truncated ? 'truncated' : 'done')
      onGenerated?.(result.text)
    } catch (err) {
      const code = asAiErrorCode(extractErrorCode(err))
      if (code === undefined) {
        // 拿不到码（超时/断网/CORS）或出现未登记的码 ⇒ 通用文案，而不是假定成某一个具体的码
        setErrorText(extractErrorMessage(err, t(`${KEY}.aiDraftFailed`)))
      } else {
        setErrorKey(MESSAGE_KEY[code])
      }
      setStatus('error')
      // ⚠️ 刻意不碰 draft（见类注释：这里是内存里的唯一一份）
    }
  }

  const copy = () => {
    void navigator.clipboard.writeText(draft)
    message.success(t(`${KEY}.aiDraftCopied`))
  }

  return (
    <>
      <Button icon={<ThunderboltOutlined />} onClick={() => setOpen(true)}>
        {t(`${KEY}.aiDraftButton`)}
      </Button>
      <Modal
        open={open}
        title={t(`${KEY}.aiDraftModalTitle`)}
        width={720}
        footer={null}
        onCancel={() => setOpen(false)}
      >
        <Typography.Paragraph type="secondary">{t(`${KEY}.aiDraftHint`)}</Typography.Paragraph>
        {status === 'generating' && <Alert type="info" message={t(`${KEY}.aiDraftGenerating`)} showIcon />}
        {errorKey !== null && <Alert type="error" message={t(errorKey)} showIcon />}
        {errorText !== '' && <Alert type="error" message={errorText} showIcon />}
        {status === 'truncated' && <Alert type="warning" message={t(`${KEY}.aiDraftTruncated`)} showIcon />}
        {status === 'done' && <Alert type="success" message={t(`${KEY}.aiDraftDone`)} showIcon />}
        <Input.TextArea
          aria-label={t(`${KEY}.aiDraftAria`)}
          placeholder={t(`${KEY}.aiDraftPlaceholder`)}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          rows={10}
          style={{ marginTop: 12 }}
        />
        <Space style={{ marginTop: 12 }}>
          <Button
            type="primary"
            icon={<ThunderboltOutlined />}
            loading={status === 'generating'}
            disabled={status === 'generating'}
            onClick={() => void generate()}
          >
            {generatedOnce ? t(`${KEY}.aiDraftRegenerate`) : t(`${KEY}.aiDraftGenerate`)}
          </Button>
          <Button icon={<CopyOutlined />} disabled={draft === ''} onClick={copy}>
            {t(`${KEY}.aiDraftCopy`)}
          </Button>
        </Space>
      </Modal>
    </>
  )
}
