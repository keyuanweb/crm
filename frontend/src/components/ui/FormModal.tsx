/**
 * 表单弹窗原语（088 交付物 2）。
 *
 * ## 它收口的是一份"每次都要重新想一遍"的清单
 *
 * 实测 70 个 `Modal` 里 58 个承载表单，而：
 *
 * | 属性 | 现状 |
 * |---|---|
 * | `width` | **11 种取值**，另有 **32 个根本没设宽度**（吃 antd 默认 520） |
 * | `okText` | 60/70 设了，**缺 10** |
 * | `cancelText` | **0/70**——全是 antd 默认的 "Cancel"，中英文界面下都不对 |
 * | `confirmLoading` | 36/70，**23 个表单弹窗缺**（提交期间按钮可重复点） |
 * | `destroyOnClose` | 62/70 |
 *
 * 每一项单看都很小，合起来就是"同一个弹窗契约在 58 处各写一遍，且每次都漏一项"。
 * 本组件把那 5 项变成默认值。
 *
 * ## ⚠️ `destroyOnClose` 的拼写**不要"顺手修"**
 *
 * 当前 antd 是 **5.22.0**，正确拼写就是 `destroyOnClose`；`destroyOnHidden` 是 **5.25**
 * 才引入的改名。全库实测 `destroyOnClose` 62 处、`destroyOnHidden` **0 处**
 * （见 `research.md` §2.4）——它们**都是对的**。按新版本拼写改会得到一个
 * 静默失效的属性（React 不认识的 prop 直接透传，antd 忽略，无任何报错）。
 *
 * ## 为什么**不**在这里做"自定义 footer + Enter 提交"
 *
 * 那是本批次留给你在样板验收时拍板的第 4 项决策。它会把 `validateFields()` 从
 * 58 个页面里删掉，但也让 OK 按钮不再是 antd 默认脚注——**验收前不预设结论**。
 * 所以 P1 的 `FormModal` 只做默认值收口 + 提供 `footer` 透传口，行为对现有页面是中性的。
 */

import { useState } from 'react'
import { Modal } from 'antd'
import type { ModalProps } from 'antd'
import { useTranslation } from 'react-i18next'
import { FORM_MODAL_WIDTHS, type FormModalSize } from './formModalSize'
export interface FormModalProps extends Omit<ModalProps, 'width' | 'onOk' | 'confirmLoading'> {
  /** 宽度档位。默认 `md`(640)。 */
  size?: FormModalSize
  /** 显式宽度，覆盖 `size`。仅在确实不在四档之内时使用（例如需要 `'90vw'`）。 */
  width?: number | string
  /**
   * 提交回调。**在它 resolve 之前 OK 按钮一直处于 loading**。
   * 抛错时 loading 会复位，但**不吞异常**——错误提示仍由调用方的
   * `message.error(extractErrorMessage(err))` 负责（全库既有约定）。
   */
  onSubmit?: () => void | Promise<void>
}

export default function FormModal({
  size = 'md',
  width,
  onSubmit,
  okText,
  cancelText,
  footer,
  children,
  ...rest
}: FormModalProps) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)

  const handleOk = async () => {
    if (!onSubmit) return
    try {
      setSubmitting(true)
      await onSubmit()
    } finally {
      // 放在 finally 而不是 then 之后：onSubmit 抛错时若不复位，
      // 弹窗会永久卡在 loading，用户连重试都点不了。
      setSubmitting(false)
    }
  }

  return (
    <Modal
      // `...rest` 在前：下面这些是**契约**，不允许调用方从 rest 里悄悄覆盖。
      // （`width` / `okText` / `cancelText` / `footer` 已具名解构，不在 rest 中，
      //  它们是可以覆盖的——契约与可选项的区别就在这里。）
      {...rest}
      width={width ?? FORM_MODAL_WIDTHS[size]}
      destroyOnClose // 见文件头：5.22.0 的正确拼写，**不要**改成 destroyOnHidden
      okText={okText ?? t('common.button.save')}
      cancelText={cancelText ?? t('common.button.cancel')}
      confirmLoading={submitting}
      onOk={onSubmit ? handleOk : undefined}
      footer={footer}
    >
      {children}
    </Modal>
  )
}
