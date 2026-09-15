/**
 * `FormGrid` 的栅格算术。**单独成文件**有两个理由，两个都不是洁癖：
 *
 * 1. `react-refresh/only-export-components`（`eslint.config.js:21`）要求组件文件只导出组件——
 *    本仓库当前的 lint 是**零警告**的，为两个纯函数破例会让后续的人以为"加一条 disable 就行"。
 * 2. 更要紧的是：这两个函数的**每一条分支都必须能被断言**。它们需要能不经渲染就被调用，
 *    而这恰恰是渲染测试做不到的部分（见下）。
 *
 * ## 为什么这里的断言必须走纯函数，不能走 DOM
 *
 * jsdom 的 `cssstyle` **不反射 `gridTemplateColumns` / `columnGap` / `rowGap`**
 * （`display`、`width` 则会）。也就是说：即便把 `FormGrid` 渲染出来，
 * `expect(el).toHaveStyle({ columnGap: '16px' })` 也**永远是假的**——
 * 不是断言写得不对，是环境里根本没有这个值。
 * 所以渲染测试只钉 `display: grid` 与"子节点不被包 wrapper"，
 * 栅格算术的全部结论在这里以纯函数形式钉住。
 */

import type React from 'react'

/**
 * 栅格模板串。
 *
 * ⚠️ **`min(100%, Npx)` 里的这个 `min()` 不能省。**
 *
 * `repeat(auto-fit, minmax(Npx, 1fr))` 里的 `N` 是**轨道下限**：容器比 N 还窄时，
 * 轨道不收缩，而是**撑破容器**。320px 视口下 antd 把弹窗夹到 `calc(100vw - 32px)` = 288px，
 * 扣掉内边距约 248px，而 `N = labelWidth + 160 = 256` > 248 → 横向溢出。
 * 写成 `minmax(min(100%, Npx), 1fr)` 后下限变成"N 与容器宽度的较小者"，
 * 最窄情形退化成单列铺满。今天 `span={12}` 的病灶正是这个溢出。
 *
 * 省掉 `min()` 的版本在**宽容器下与正确版完全一样**，只有窄屏才炸——
 * 即"样板页全绿、线上窄屏溢出"的形态，所以测试里有一条专门断言它含 `min(100%,`。
 */
export function formGridTemplate(input: {
  cols?: number
  minItemWidth: number
  maxCols?: number
  gutter: number
}): string {
  if (input.cols) return `repeat(${input.cols}, minmax(0, 1fr))`
  const track = isUsableCap(input.maxCols)
    ? `min(100%, max(${input.minItemWidth}px, ${formGridCap(input.gutter, input.maxCols)}))`
    : `min(100%, ${input.minItemWidth}px)`
  return `repeat(auto-fit, minmax(${track}, 1fr))`
}

/** `maxCols` 只在这一档以内生效；其余（未传 / 0 / 负数 / 小数）一律退回"不限列"。 */
function isUsableCap(maxCols: number | undefined): maxCols is number {
  return maxCols !== undefined && Number.isInteger(maxCols) && maxCols >= 1
}

/**
 * `maxCols` 的实现手法：**抬高轨道下限**，而不是换成写死列数。
 *
 * ## 为什么不能直接 `cols={3}`
 *
 * `cols={3}` 生成的是 `repeat(3, minmax(0, 1fr))`——**三列，与容器宽无关**。
 * 它在 320px 视口下照样三列，每个字段被压到 100px 以下；那正是本组件存在的理由
 * （把"该自动的东西"从约定降级回机制）被反着做一遍。
 * 上限要的是"**最多**三列"，窄容器仍要跟着降列。
 *
 * ## 由轨道数公式反解
 *
 * `repeat(auto-fit, minmax(T, 1fr))` 的轨道数 `k = floor((W + g) / (T + g))`
 * （`W` = 容器内容宽，`g` = `gutter`）。要让 `k` 恒 `≤ C`，取
 * `T > (W + g) / (C + 1) - g`。
 *
 * **那个 `+1px` 不能省**：等号那一刻 `k` 恰好等于 `C + 1`（`(W+g)/(T+g)` 正好是 `C+1`，
 * 取整后落到不该去的那一侧），而 `W` 是百分比、由布局引擎浮点解析，
 * 落在等号上就是"有时 3 列、有时 4 列"。留 1px 余量把它压进安全区
 * （每个轨道最多为此少 1px，而轨道是 `1fr`，实际宽度由容器平分，看不出差别）。
 *
 * ## 与 `minItemWidth` 的分工
 *
 * 两者**不是**二选一，而是取大者：容器窄时余量项很小、`minItemWidth` 说了算
 * （此时行为与不设上限**逐字节相同**）；容器宽过 `minItemWidth * (C + 1) + C * g - 1`
 * 之后才由上限接管。切换点是连续的——`W` 恰好等于该值时两式相等，
 * 故不会出现"从 2 列直接跳到 4 列"的断口。
 */
export function formGridCap(gutter: number, maxCols: number): string {
  return `calc((100% + ${gutter}px) / ${maxCols + 1} - ${gutter}px + 1px)`
}

/** `FormGrid` 的完整内联样式。 */
export function formGridStyle(input: {
  cols?: number
  minItemWidth: number
  maxCols?: number
  gutter: number
}): React.CSSProperties {
  return {
    display: 'grid',
    gridTemplateColumns: formGridTemplate(input),
    columnGap: input.gutter,
    // 纵向间距**交给 Form.Item 自己的 marginBottom**（主题里已从 24 收到 12），
    // 这里再加 rowGap 会让两者叠加，出现 12+16 这种谁也说不清来源的空隙。
    rowGap: 0,
  }
}
