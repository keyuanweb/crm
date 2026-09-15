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
 * ## 第 4 项决策的落地：**只加 Enter，不改 footer**（2026-09-15 用户裁决）
 *
 * plan 第 4 项的原话是「`FormModal` 用自定义 footer + Enter 提交，取代 antd 默认的
 * `Modal.onOk` 脚注」，推荐「采用」。**验收时只采纳了后半句**，理由记在这里：
 *
 * - 那一项自称的价值是「把 `validateFields()` 从 ~58 个页面里删掉」，而这件事
 *   **`onSubmit` 已经做到了**——调用方不再自己 `validateFields()`，异常与 loading
 *   也由本组件兜住。自定义 footer 并不是这项收益的来源。
 * - 自定义 footer 的代价是 OK 按钮不再是 antd 默认脚注，而全站 9 个自己写了
 *   `footer=` 的弹窗（contacts / leads / departments / marketing / portal / tasks /
 *   open×2 / map）**用的都是裸 `<Modal>`、根本不经过本组件**。即：为一个不带来
 *   收益的改动，去动 9 个与它无关的页面。
 * - `footer` 的透传口**保留**（见下方 `footer={footer}`），将来改主意不必再动契约。
 *
 * ## ⚠️ Enter 的锚点**不能**挂在 `<Modal>` 上（踩过的坑）
 *
 * `<Modal onKeyDown={...}>` **静默失效**。追一遍链路：antd 把未知 prop 收进
 * `restProps` 交给 rc-dialog 的 `Dialog`（`antd/es/modal/Modal.js:126`），`Dialog`
 * 再 `{...props}` 传给 `Content`，而 `Content` **只解构自己认识的那些**再交给
 * `Panel`，`Panel` 也只写死属性（`rc-dialog/es/Dialog/Content/Panel.js:116-139`）。
 * 于是 `onKeyDown` 一层层被丢掉——**不报错、不警告、DOM 上也没有**，只能靠行为发现。
 *
 * 所以锚点是一层自己的包裹 `<div onKeyDown>`。加它之前核对过库的 DOM 结构：
 * 本库 CSS 对 `.ant-modal-body` 的子级无选择器依赖（`index.css` 里与弹窗相关的
 * 只有一条 `.ant-modal .ant-row .ant-col`），故这层包裹是安全的。
 *
 * **判据本体（哪两类控件上的 Enter 必须不归它管）在 `./formModalEnter.ts`**——
 * 拆出去不是风格偏好，是 `react-refresh/only-export-components` 逼的，见那个文件的文件头。
 */

import { useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Modal } from 'antd'
import type { ModalProps } from 'antd'
import { useTranslation } from 'react-i18next'
import { shouldSubmitOnEnter } from './formModalEnter'
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

  /**
   * Enter 提交。与 OK 按钮**共用同一个 `handleOk`**，故两条路径的 loading 生命周期、
   * 「异常不吞」、以及「没给 `onSubmit` 就是空操作」全都一致——不另起一套提交逻辑。
   */
  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    // 没给 `onSubmit` 时 OK 按钮本身就是空操作，Enter 必须同样空转，否则两条路径语义分叉。
    if (!onSubmit || submitting) return
    if (!shouldSubmitOnEnter(event)) return
    // 不 `preventDefault` 的话，INPUT 上的 Enter 会触发**隐式表单提交**，
    // 而这里的孩子通常就是 `<Form>`（真实 `<form>` 元素）——一次按键提交两遍。
    event.preventDefault()
    void handleOk()
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
      {/* Enter 的锚点必须是自己的元素：挂在 `<Modal>` 上会静默失效，见文件头。 */}
      <div onKeyDown={handleKeyDown}>{children}</div>
    </Modal>
  )
}
