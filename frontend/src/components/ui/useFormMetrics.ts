/**
 * 表单度量的**单一来源**（088 交付物 2）。
 *
 * 它收口两件此前各页各写一遍的事：
 *
 * 1. **标签宽度**。现状有 5 种取值散在全库（`100px`×11、`90px`×6、`110px`×2、`80px`×2、`70px`×1，
 *    见 `specs/088-frontend-layout-consistency/research.md` §2.2）——**它们不承载任何设计意图，
 *    只是各页各写了一遍**。收成一个来源后，"统一的标签宽度"才有唯一真值。
 *
 * 2. **栅格下限**。`FormGrid` 的 `auto-fit` 需要一个"再窄就不能并排"的宽度，
 *    它等于 `labelWidth + 一个输入框的最小可用宽度`。把它算在这里，是为了让
 *    "标签变宽 ⇒ 栅格下限跟着变宽"这件事**自动成立**，而不是两处各配一个数。
 *    （T041 起本模块还出**竖向**表单的下限 `VERTICAL_MIN_ITEM_WIDTH`——竖向标签在上、
 *    不占横向空间，所以那是**另一个数**，但不是另一套机制，推导见那个常量的注释。）
 *
 * ## 为什么标签宽度要按语言取值
 *
 * 中文 96px 装得下"联系方式"，英文同一个位置的 "Contact Person" 需要约 112px。
 * 若图省事写死 96px，英文界面下标签会**折成两行**——那就变成了"为了一致性而引入的
 * 英文专属回归"。
 *
 * ## 为什么语言判断被抽成一个**纯函数**（而不是留在 hook 里）
 *
 * 因为 hook 里的语言分支**测不到**：测试环境把 `react-i18next` 全局打桩成
 * `{ language: 'zh', ... }`（`src/test/setup.ts:115`），那个 mock 是 **72 个测试文件共享的**，
 * 为了测一个英文分支去改它，代价与收益完全不成比例。抽成 `labelWidthFor(language)` 后，
 * 分支逻辑可以用一次纯函数调用覆盖干净，hook 自己反而**不再含语言分支**。
 *
 * ## 已知边界
 *
 * 只读 `i18n.language`，不订阅其变化——`react-i18next` 的 `useTranslation()` 本身会在
 * 语言切换时触发重渲染，故拿到的值总是当前的。测试环境的 mock 只提供
 * `{ language, changeLanguage }`（见 `src/test/setup.ts`），**故此处不得使用
 * `resolvedLanguage` / `on()` 等 mock 上没有的成员**——用了会在测试里炸，
 * 而生产没问题，属于最难查的那类不一致。
 */

import { useTranslation } from 'react-i18next'

/** 标签宽度（px）。按语言取值，理由见文件头。 */
export const LABEL_WIDTH = { zh: 96, en: 112 } as const

/**
 * 一个输入框的最小可用宽度（px）。160 是"能看清 12~15 个字符"的经验下限：
 * 再窄的话日期、金额、下拉选项都会被截断，横向并排就失去意义了。
 */
export const MIN_FIELD_WIDTH = 160

/**
 * **竖向**（标签在**上**方）表单的栅格下限（px）。088 的 3.2 批次（T041）用这个值。
 *
 * 与上面 `minItemWidth` 的唯一区别：竖向表单的标签不占横向空间，所以下限里**不该含 `labelWidth`**。
 *
 * ## 为什么是 200 而不是"控件下限 160"
 *
 * 因为它不是拍的，是一个**推导出来的窗口**。要在默认档 `md` = 640
 * （可用宽 = 640 − 24×2 = 592）**恰好排两列**，下限 `F` 必须**同时**满足：
 *
 * | 条件 | 不等式 | 由来 |
 * |---|---|---|
 * | 放得下两列 | `2F + 16 ≤ 592` | `2F` 加上一个列间距 |
 * | **放不下三列** | `3F + 2×16 > 592` | 这一条才是"恰好两列"的承重项 |
 *
 * ⇒ `F ∈ (187, 288]`。取窗口内**最贴近控件下限**（`MIN_FIELD_WIDTH` = 160）的值 = **200**。
 *
 * 若哪天默认档宽度改了，**这个数要跟着重推**——测试里钉的是那两条不等式，不是 `200` 本身。
 *
 * ## 窄视口不需要额外保护
 *
 * 320px 视口把弹窗夹到 288px，`⌊(288+16)/(200+16)⌋ = 1` ⇒ 自动退化成单列铺满。
 * 这正是 `min(100%, N)` 那一条要的效果，也是竖向表单**不能**写死 `cols={2}` 的原因：
 * `repeat(2, minmax(0,1fr))` 在 248px 容器里会给出两个 116px 的控件。
 */
export const VERTICAL_MIN_ITEM_WIDTH = 200

/** 定宽横标签的宽度（px）。纯函数，见文件头「为什么语言判断被抽成一个纯函数」。 */
export function labelWidthFor(language: string | undefined): number {
  return (language ?? '').startsWith('en') ? LABEL_WIDTH.en : LABEL_WIDTH.zh
}

export interface FormMetrics {
  /** 定宽横标签的宽度（px）。 */
  labelWidth: number
  /** `FormGrid` 的 `auto-fit` 下限 = labelWidth + MIN_FIELD_WIDTH。 */
  minItemWidth: number
}

export function useFormMetrics(overrides?: {
  labelWidth?: number
  minFieldWidth?: number
}): FormMetrics {
  const { i18n } = useTranslation()
  const labelWidth = overrides?.labelWidth ?? labelWidthFor(i18n.language)
  const minFieldWidth = overrides?.minFieldWidth ?? MIN_FIELD_WIDTH
  return { labelWidth, minItemWidth: labelWidth + minFieldWidth }
}

export default useFormMetrics
