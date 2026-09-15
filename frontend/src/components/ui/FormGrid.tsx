/**
 * 表单栅格原语（088 交付物 2，本批次的核心）。
 *
 * ## 它解决的是哪一个真问题
 *
 * 实测：102 个表单 `<Col>` 里 **96 个（94%）写死 `span` 且无任何响应式断点**，
 * 全库仅 6 个响应式表单 Col（全在 `products/ProductListPage.tsx`），
 * 且**没有任何一处把 `span` 与 `xs/sm` 并用**
 * （见 `specs/088-frontend-layout-consistency/research.md` §2.3）。
 * 后果是 320px 屏上 `span={12}` 仍然是两列——每列 130px，日期控件直接被截断。
 *
 * ## 为什么不用视口断点（`xs/sm/md`）
 *
 * 因为**断点测错了东西**。本仓库 58 个表单住在 Modal 里，Modal 的宽度不是视口决定的：
 * 一个 960px 的视口里可能开着 480px 的弹窗。此时 `md` 断点说"够宽，排两列"，
 * 而弹窗里其实只有 432px 可用——于是又回到挤压。
 *
 * 所以列数由**容器自己的宽度**推导（CSS Grid `auto-fit` + `minmax`），
 * 弹窗宽窄变化时自动跟随，**不需要任何人记住"哪档宽度配几列"**。
 * 这一条是本文件存在的全部理由：把一个本该自动的东西从"约定"降级回"机制"。
 *
 * ## `min(100%, Npx)` 这个 `min()` 不能省
 *
 * `repeat(auto-fit, minmax(${N}px, 1fr))` 里的 `N` 是**轨道下限**：
 * 容器比 N 还窄时，轨道不会收缩，而是撑破容器。
 * 320px 视口下 antd 把弹窗夹到 `calc(100vw - 32px)` = 288px，扣掉内边距约 248px，
 * 而 `N = labelWidth + 160 = 256` > 248 → **横向溢出**。
 * 写成 `minmax(min(100%, Npx), 1fr)` 后，下限变成"N 与容器宽度的较小者"，
 * 于是最窄情形退化成单列铺满，而不是溢出一条看不见的边界。
 * 今天 `span={12}` 的病灶正是这个溢出。
 * 这段算术的实现在 `./formGridStyle.ts`——**单独成文件是因为它必须能被纯函数断言**，
 * 详见那个文件的头注释（jsdom 不反射 `gridTemplateColumns` / `gutter`）。
 *
 * ## 使用纪律（代码评审把关，**不进门禁**）
 *
 * 1. **`FormGrid` 只包"成对的字段"**。全宽项（`Input.TextArea`、
 *    `<CustomFieldFormItems>`、`<Divider>`、备注类长文本）要放在 grid **之后**作为兄弟节点。
 *    放进 grid 会让它悄悄退化成 N 列里的**一列**——这是本组件最可能的误用，
 *    而静态判断"哪个字段该全宽"不现实，所以刻意不做机器校验。
 * 2. **同一表单内不得混用 `FormGrid` 与 `<Col>`**：两套栅格叠加会产生
 *    上一层 `Row/Col` 的分栏再被内层 grid 分一次，结果既不是 2 列也不是 4 列。
 */

import React from 'react'
import { useFormMetrics } from './useFormMetrics'
import { formGridStyle } from './formGridStyle'

export interface FormGridProps {
  children: React.ReactNode
  /**
   * 固定列数。**默认不传**，走 `auto-fit` 由容器宽度推导——绝大多数场景都该省略它。
   * 仅在"这一组必须恰好 N 列，哪怕挤"时显式传（例如两个 1 位数字的并排输入）。
   */
  cols?: number
  /** 单列的最小宽度（px）。默认 = `labelWidth + 160`，由 `useFormMetrics` 给出。 */
  minItemWidth?: number
  /**
   * **列数上限**。默认不传 = 不限（纯 auto-fit）。
   *
   * 与 `cols` 的区别就是"最多 N 列"与"恰好 N 列"：`cols` 在窄屏会硬挤出 N 列，
   * 本项则只封顶、不封底（窄容器照旧跟着降列）。实现见 `formGridCap`。
   *
   * **页面级表单要传它**：弹窗宽度是 480–960 的窄容器，auto-fit 的列数天然合理；
   * 而页面级容器约 980px（1920 视口下更宽），下限 200 会排出 4–5 列——
   * 那时"容器驱动"就不再是优点而成了漂移。弹出档位由 088 T043 裁决为 **3**。
   *
   * 传 `cols` 时本项被忽略（已有恰好 N 列的定论，无"上限"可言）；传 0/负数/小数按未传处理。
   */
  maxCols?: number
  /** 列间距（px）。默认 16，与 `Form.itemMarginBottom: 12` 同一量级。 */
  gutter?: number
  style?: React.CSSProperties
}

export default function FormGrid({
  children,
  cols,
  minItemWidth,
  maxCols,
  gutter = 16,
  style,
}: FormGridProps) {
  const metrics = useFormMetrics()
  const min = minItemWidth ?? metrics.minItemWidth

  // 调用方的 `style` 在后：允许覆盖（例如某个页面要临时加 `marginTop`），
  // 但默认值全部来自上面那个纯函数，页面上不再各写一遍栅格参数。
  const mergedStyle = { ...formGridStyle({ cols, minItemWidth: min, maxCols, gutter }), ...style }

  return (
    <div data-testid="form-grid" style={mergedStyle}>
      {children}
    </div>
  )
}
