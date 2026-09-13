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
export function formGridTemplate(cols: number | undefined, minItemWidth: number): string {
  return cols
    ? `repeat(${cols}, minmax(0, 1fr))`
    : `repeat(auto-fit, minmax(min(100%, ${minItemWidth}px), 1fr))`
}

/** `FormGrid` 的完整内联样式。 */
export function formGridStyle(input: {
  cols?: number
  minItemWidth: number
  gutter: number
}): React.CSSProperties {
  return {
    display: 'grid',
    gridTemplateColumns: formGridTemplate(input.cols, input.minItemWidth),
    columnGap: input.gutter,
    // 纵向间距**交给 Form.Item 自己的 marginBottom**（主题里已从 24 收到 12），
    // 这里再加 rowGap 会让两者叠加，出现 12+16 这种谁也说不清来源的空隙。
    rowGap: 0,
  }
}
