/**
 * `FormModal` 的 Enter 提交判据。
 *
 * ## 为什么单独一个文件
 *
 * 它原本写在 `FormModal.tsx` 里。**不是风格选择**——`eslint` 的
 * `react-refresh/only-export-components` 会报 warning（组件文件里导出非组件会让
 * Fast Refresh 失效），而本仓的 lint 门禁要求**零 warning 且不许 `eslint-disable`**。
 * 同目录的 `formModalSize.ts` 是同一个原因拆出去的先例。
 *
 * ## 判据的由来
 *
 * 2026-09-15 用户对 plan 第 4 项的裁决是「**只加 Enter，不改 footer**」。
 * Enter 提交一旦做成全局绑定，风险全在**误触**上——所以这个函数的重心
 * 不是「什么时候提交」，而是「哪两类控件上的 Enter 必须不归我管」。
 */

import type { KeyboardEvent } from 'react'

/**
 * 这次按键该不该触发提交。
 *
 * 抽成**纯函数**而不是内联进组件：它是这条链路上唯一带分支判定的逻辑，
 * 而下面两个排除项各自对应一类真实误触，值得单独被断言、也便于将来加白。
 */
export function shouldSubmitOnEnter(event: KeyboardEvent<HTMLElement>): boolean {
  if (event.key !== 'Enter') return false
  const el = event.target as HTMLElement | null
  if (!el || typeof el.tagName !== 'string') return false

  // ① antd 的下拉类控件用 Enter **确认选项**——Select / DatePicker / TimePicker /
  //    AutoComplete 的输入框都落在 `.ant-select` 或 `.ant-picker` 里。
  //    不排除的话，用户选完一个日期就把整个表单提交了。
  //    （Cascader / TreeSelect / Mentions 在本库零使用，故不为它们写选择器。）
  if (el.closest('.ant-select, .ant-picker')) return false

  // ② 只认真正的文本输入。
  //
  //    这一条**同时**排掉了多行输入：`TextArea` 的 tagName 是 `TEXTAREA`、不等于 `INPUT`，
  //    所以 Enter 在公告正文 / 跟进记录 / 备注里仍是换行（全库 33 个文件用 TextArea）。
  //    这里本来**另写了一条** `if (el.tagName === 'TEXTAREA') return false`，
  //    定向破坏证明它是**死分支**：把它改成恒不成立，17 条用例全绿——因为下面这行
  //    已经兜住了。故删掉那条，把「为什么 TextArea 不会误提交」记在这里，
  //    而不是留一行不起作用的代码配一句"它很要紧"的注释去骗下一个人。
  //
  //    按钮也不在其中：按钮上的 Enter 由浏览器自己合成 click，这里再交一次就是双发。
  return el.tagName === 'INPUT'
}
