export { default as StatCard } from './StatCard'
export type { StatCardProps } from './StatCard'

export { default as StatusTag } from './StatusTag'
export type { StatusTagProps, StatusTagType } from './StatusTag'

export { default as AmountDisplay } from './AmountDisplay'
export type { AmountDisplayProps } from './AmountDisplay'

// 088：表单与布局原语。**刻意放在既有的 `components/ui/` 里**——
// 077 建过一套设计系统，此后再没被扩展过，全库只剩 4 个详情页 import 了这个 barrel
// （见 research.md §2.10）。另起一个 `src/components/form/` 正是造成那次失败的形态：
// 第二个平行的 UI 目录会让"设计系统"再次退化成两套各覆盖一半的库。
export { default as FormGrid } from './FormGrid'
export type { FormGridProps } from './FormGrid'

export { default as FormModal } from './FormModal'
export type { FormModalProps } from './FormModal'

// 档位表与栅格算术刻意**不**从这里导出：它们是组件的实现细节，
// 导出会诱使页面自己拼宽度 / 自己拼 grid-template-columns，那就又回到"每页各写一遍"。
// 需要断言它们的测试按显式路径 import（`./formGridStyle`、`./formModalSize`）。
export { FORM_MODAL_WIDTHS } from './formModalSize'
export type { FormModalSize } from './formModalSize'

export { default as PageState } from './PageState'
export type { PageStateProps, PageStateKind } from './PageState'

// 095：自 `pages/search/SearchResultPage.tsx` 提取的关键字高亮（一份实现两处用）。
// 该页现从本 barrel 引入——**R7 的「被引用」正是靠这一层再导出满足的**，
// 若把 Highlight 从 barrel 去掉，`ui:check` 会判它孤儿。
export { default as Highlight } from './Highlight'
export type { HighlightProps } from './Highlight'

export { useFormMetrics, LABEL_WIDTH, MIN_FIELD_WIDTH, VERTICAL_MIN_ITEM_WIDTH } from './useFormMetrics'
export type { FormMetrics } from './useFormMetrics'
