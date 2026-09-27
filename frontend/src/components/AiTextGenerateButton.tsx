import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, App, Button, Input, Modal, Space, Typography } from 'antd'
import { CopyOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { extractErrorCode, extractErrorMessage } from '../services/apiClient'
import { asAiErrorCode, type AiErrorCode, type AiGenerationResult } from '../types/aiContent'

/**
 * 「生成文本」按钮 + 结果模态框的**共用外壳**（104-ai-content-generation；P1 邮件草稿、P2 客户 360 摘要）。
 *
 * <p><b>为什么抽外壳而不是各写一份</b>：两个能力的读屏名、四态、失败态保留编辑区、错误码 ⇒ 文案的分派
 * **逐字相同**；差异只有两点——**打哪个端点**、**文案键用哪一组前缀**。各写一份就是把「同一个
 * `Record<AiErrorCode, string>` 总性」与「失败态不清空编辑区」这两条纪律复制成两处表述，而它们一旦
 * 漂移，**没有任何判据会发现**（P2 的用例只会各自钉自己那一份）。
 *
 * <p><b>四态</b>（plan.md 的 F1–F4）：未配置（后端 409 `AI_NOT_CONFIGURED`）/ 生成中 / 成功 /
 * 截断。四态都由 {@link Status} 一个状态位承担——分成几个布尔量就会出现"生成中且成功"这类
 * 没有意义的组合，而界面必须对每一种组合都给出一个（正确的）画面。
 *
 * <p><b>为什么失败态不清空编辑区</b>：用户可能在上一版结果上手改过一段，一次失败的重新生成
 * 不该把那段抹掉。这不是体贴，是**数据丢失**：本组件没有任何持久化（104 零新增表，草稿不落库，
 * 见 spec.md FR-019 的 ✅ 订正），编辑区里的字是内存里的唯一一份。故 catch 里只改状态位，
 * **不碰 `draft`**。
 *
 * <p><b>为什么错误文案按码分派而不是直接显示后端 message</b>：两个 403 出口（权限切面
 * `PERMISSION_DENIED` 与数据范围 `FORBIDDEN`）与 429 的两种成因（突发限流 / 日预算耗尽）
 * 对用户是**不同的下一步**；后端的中文文案是给人看的兜底，不是给分支用的判据。映射写成
 * `Record<AiErrorCode, string>`，于是**加了一个码却忘了它的文案 = 编译期报错**（而非界面上多一片空白）。
 *
 * <p><b>出站调用为什么是调用方传进来的闭包（而不是把 id 收进本组件）</b>：两个端点的请求体**不同形**
 * （P1 = `{customerId, opportunityId?}`，P2 = `{customerId}`），而「请求体里到底带了哪几个字段」
 * 是本能力**最容易被接错**的一处（见 `AiCustomerSummaryButton.test.tsx` 的 F5-a）。让每个能力在自己的
 * 组件里、对着自己的 `*Request` 类型构造请求体，本外壳就**看不见 id**——它也就没有机会把两个能力的
 * 字段混在一起。
 *
 * <p>⚠️ <b>视觉必须与 022 的智能建议区分</b>：022 的约定是琥珀金 `#faad14` + `BulbOutlined` +
 * `ai-icon-pulse` 脉冲（`DashboardPage.tsx` / `index.css`）。本组件用的是**闪电图标 + antd 语义色
 * 的原生 Alert/Button**，没有任何自定义色值与动画——两个"AI"在同一屏里不会互相冒充（`check-ui.mjs`
 * R1 也禁止组件里出现品牌色字面量）。
 */
const KEY = 'pages.customer.detail'

/** 受控码 ⇒ 文案键**后缀**（前缀由调用方给）。`Record<AiErrorCode, string>` 的总性 = 缺分支在编译期被挡。 */
const ERROR_SUFFIX: Record<AiErrorCode, string> = {
  AI_NOT_CONFIGURED: 'NotConfigured',
  AI_GENERATION_REJECTED: 'Rejected',
  AI_UPSTREAM_UNAVAILABLE: 'UpstreamUnavailable',
  RATE_LIMITED: 'RateLimited',
  PERMISSION_DENIED: 'Forbidden',
  FORBIDDEN: 'Forbidden',
}

/** 未配置 / 生成中 / 成功 / 截断 / 失败——四态外加一个"还没点过"的初始态。 */
type Status = 'idle' | 'generating' | 'done' | 'truncated' | 'error'

export interface AiTextGenerateButtonProps {
  /**
   * 文案键前缀：本组件的全部文案都是 `pages.customer.detail.` + 前缀 + 后缀。
   *
   * <p>写成**联合类型**而不是 `string`：`aiDraft` / `aiSummary` 这两个前缀各自对应语言文件里的一组
   * 键，写错一个字母**没有任何门禁看得见**（`check-i18n.mjs` 只比对两份语言文件之间的键集合，
   * 不扫源码用法；组件里渲染出的是键名字面量，而测试把 `t` 桩成恒等函数，两边一起错就一起绿）。
   * 收成联合类型之后，"前缀拼错"在**编译期**就报错。
   */
  keyPrefix: 'aiDraft' | 'aiSummary'
  /** 出站调用（由能力自己的组件闭包它自己的请求体）。 */
  generate: () => Promise<AiGenerationResult>
  /** 每次**成功**生成调用一次，参数是返回的**原始**文本（宿主自行决定拿它做什么）。 */
  onGenerated?: (text: string) => void
}

export default function AiTextGenerateButton({
  keyPrefix,
  generate,
  onGenerated,
}: AiTextGenerateButtonProps) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  /** 是否成功生成过（只影响按钮文案：生成 / 重新生成）——与"结果非空"不是同一件事，见 F1。 */
  const [generatedOnce, setGeneratedOnce] = useState(false)
  const [errorKey, setErrorKey] = useState<string | null>(null)
  const [errorText, setErrorText] = useState('')

  const k = (suffix: string) => `${KEY}.${keyPrefix}${suffix}`

  const run = async () => {
    // 双保险：按钮此时是 disabled，但"点击"这件事在某些环境里照样会到达处理器（F2 的判据就是它）。
    if (status === 'generating') return
    setStatus('generating')
    setErrorKey(null)
    setErrorText('')
    try {
      const result = await generate()
      setDraft(result.text)
      setGeneratedOnce(true)
      setStatus(result.truncated ? 'truncated' : 'done')
      onGenerated?.(result.text)
    } catch (err) {
      const code = asAiErrorCode(extractErrorCode(err))
      if (code === undefined) {
        // 拿不到码（超时/断网/CORS）或出现未登记的码 ⇒ 通用文案，而不是假定成某一个具体的码
        setErrorText(extractErrorMessage(err, t(k('Failed'))))
      } else {
        setErrorKey(k(ERROR_SUFFIX[code]))
      }
      setStatus('error')
      // ⚠️ 刻意不碰 draft（见类注释：这里是内存里的唯一一份）
    }
  }

  const copy = () => {
    void navigator.clipboard.writeText(draft)
    message.success(t(k('Copied')))
  }

  return (
    <>
      <Button icon={<ThunderboltOutlined />} onClick={() => setOpen(true)}>
        {t(k('Button'))}
      </Button>
      <Modal
        open={open}
        title={t(k('ModalTitle'))}
        width={720}
        footer={null}
        onCancel={() => setOpen(false)}
      >
        <Typography.Paragraph type="secondary">{t(k('Hint'))}</Typography.Paragraph>
        {status === 'generating' && <Alert type="info" message={t(k('Generating'))} showIcon />}
        {errorKey !== null && <Alert type="error" message={t(errorKey)} showIcon />}
        {errorText !== '' && <Alert type="error" message={errorText} showIcon />}
        {status === 'truncated' && <Alert type="warning" message={t(k('Truncated'))} showIcon />}
        {status === 'done' && <Alert type="success" message={t(k('Done'))} showIcon />}
        <Input.TextArea
          aria-label={t(k('Aria'))}
          placeholder={t(k('Placeholder'))}
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
            onClick={() => void run()}
          >
            {generatedOnce ? t(k('Regenerate')) : t(k('Generate'))}
          </Button>
          <Button icon={<CopyOutlined />} disabled={draft === ''} onClick={copy}>
            {t(k('Copy'))}
          </Button>
        </Space>
      </Modal>
    </>
  )
}
