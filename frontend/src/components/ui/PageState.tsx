/**
 * 页面三态原语：loading / empty / error（088 交付物 2）。
 *
 * ## 为什么是**一个**组件而不是三个
 *
 * 直觉上 `LoadingState` / `EmptyState` / `ErrorState` 三个小组件更"干净"，但本项目
 * 有一个具体的反作用力：`vite.config.ts` 的 `functions` 覆盖率是一条**分数**门禁，
 * 而 `specs/084-menu-ia-authorization/tasks.md` 的 T037 明令不得下调阈值。三个近乎同构的组件会为
 * 同一批调用点带来约 3 倍的函数计数，收益是"文件多两个"，代价是门禁余量。
 * 合并成一个，三条分支各自有测试覆盖，计数只算一份。
 *
 * （起草时这条还附带了一个"functions 只有 0.9pp 余量"的论据，
 * **该数字已被 P0 实测证伪**——真实余量 12.54pp。合并的设计结论不变，
 * 但**不得再引用那个数字**，见 `specs/088-frontend-layout-consistency/research.md` §4。）
 *
 * ## 三态各自的形态是刻意的
 *
 * - `loading`：居中 `Spin`。**不用骨架屏**——本仓库的列表全部由 ProTable 渲染，
 *   骨架屏会与 ProTable 自己的加载态打架，出现两层占位。
 * - `empty`：`Empty` + 可覆盖文案。默认复用既有的 `common.message.no_data`，
 *   **不新增键**（该键已存在且有译文）。
 * - `error`：`Alert type="error"` + 可选重试按钮。用 `Alert` 而不是 `Result`，
 *   因为这里是**区块级**失败（一个卡片里的表格拉不到数据），不是整页 404/500；
 *   `Result` 的体量会把所在卡片撑高到不成比例。
 *
 * ## 已知边界
 *
 * `errorText` 是**纯展示文案**，不接受 `ReactNode` 里的交互控件——重试请走 `onRetry`。
 * 这样"错误态里一定有一个可点的重试"这件事就是结构性的，而不是靠每个调用点的自觉。
 */

import React from 'react'
import { Alert, Button, Empty, Spin } from 'antd'
import { useTranslation } from 'react-i18next'

export type PageStateKind = 'loading' | 'empty' | 'error'

export interface PageStateProps {
  state: PageStateKind
  /** `state === 'error'` 时展示；同时有 `onRetry` 才渲染重试按钮。 */
  onRetry?: () => void
  /** 覆盖默认的错误文案（默认 `common.state.error`）。 */
  errorText?: React.ReactNode
  /** 覆盖默认的空态文案（默认 `common.message.no_data`）。 */
  emptyText?: React.ReactNode
  style?: React.CSSProperties
}

export default function PageState({
  state,
  onRetry,
  errorText,
  emptyText,
  style,
}: PageStateProps) {
  const { t } = useTranslation()

  if (state === 'loading') {
    return (
      <div
        data-testid="page-state-loading"
        style={{ display: 'flex', justifyContent: 'center', padding: '48px 0', ...style }}
      >
        {/* antd 5 的 `tip` 只在嵌套形态下生效；裸用时它会打警告且不显示文案。
            包一个最小高度的 div 是最轻的嵌套写法（**不要**简化成 <Spin tip=... />）。 */}
        <Spin tip={t('common.state.loading')}>
          <div style={{ minHeight: 32, width: 32 }} />
        </Spin>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <Alert
        data-testid="page-state-error"
        type="error"
        showIcon
        message={errorText ?? t('common.state.error')}
        action={
          onRetry ? (
            <Button size="small" onClick={onRetry}>
              {t('common.state.retry')}
            </Button>
          ) : undefined
        }
        style={style}
      />
    )
  }

  return (
    <div data-testid="page-state-empty" style={{ padding: '32px 0', ...style }}>
      <Empty description={emptyText ?? t('common.message.no_data')} />
    </div>
  )
}
