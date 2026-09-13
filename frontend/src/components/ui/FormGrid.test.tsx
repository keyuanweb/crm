import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import FormGrid from './FormGrid'
import { formGridStyle, formGridTemplate } from './formGridStyle'

/**
 * 表单栅格原语（088 交付物 2）。
 *
 * <p>分两层钉，与 `useFormMetrics.test.tsx` 同一套路：
 *
 * <p>① **模板串走纯函数** `formGridTemplate`。这是本组件的全部逻辑，
 * 而 jsdom 对 `grid-template-columns` 的支持取决于 `cssstyle` 版本——
 * 把"94% 的写死 span 都被这套栅格替换"这么重的结论押在"jsdom 恰好认这个属性"上，
 * 会让测试在一次无关的依赖升级中毫无理由地转红。
 *
 * <p>② **渲染测试只钉结构与既有属性**（`display: grid`、间距、子节点不被吞掉），
 * 这些是 jsdom 一定认的属性。
 *
 * <p>另有一条**反假绿**：断言默认模板里确实含 `min(100%, …)`。
 * 少了这个 `min()` 的版本在宽容器下表现与正确版**完全一样**，
 * 只有 320px 视口才炸——也就是"样板页全绿、线上窄屏溢出"的形态。
 */
describe('formGridTemplate', () => {
  it('默认（不传 cols）走 auto-fit，下限是 min(100%, 栅格宽度)', () => {
    const t = formGridTemplate(undefined, 256)
    expect(t).toBe('repeat(auto-fit, minmax(min(100%, 256px), 1fr))')
    // 反假绿：这个 min() 不能省。省掉后宽容器下看不出任何差别，窄屏才溢出。
    expect(t).toContain('min(100%,')
  })

  it('用 auto-fit 而不是 auto-fill——否则最后一行会留一条死列，3 个子项撑不满', () => {
    expect(formGridTemplate(undefined, 256)).toContain('auto-fit')
    expect(formGridTemplate(undefined, 256)).not.toContain('auto-fill')
  })

  it('显式 cols 时用 repeat(N, minmax(0, 1fr))，且下限为 0（允许子项被压缩）', () => {
    expect(formGridTemplate(3, 256)).toBe('repeat(3, minmax(0, 1fr))')
  })

  it('显式 cols 的模板里不再出现 min(100%) —— 两套下限不能混用', () => {
    expect(formGridTemplate(2, 256)).not.toContain('min(100%')
  })

  it('栅格宽度会被原样写进模板（不是被硬编码成 256）', () => {
    expect(formGridTemplate(undefined, 480)).toContain('480px')
  })
})

describe('formGridStyle', () => {
  it('完整默认样式：grid + auto-fit 模板 + 列间距 16 / 行间距 0', () => {
    expect(formGridStyle({ minItemWidth: 256, gutter: 16 })).toEqual({
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 256px), 1fr))',
      columnGap: 16,
      rowGap: 0,
    })
  })

  it('显式 cols 时不再带 min(100%) 下限（两套下限不混用）', () => {
    expect(formGridStyle({ cols: 2, minItemWidth: 256, gutter: 16 }).gridTemplateColumns).toBe(
      'repeat(2, minmax(0, 1fr))',
    )
  })

  it('gutter 只影响列间距，行间距恒为 0（纵向留白归 Form.Item 的 marginBottom）', () => {
    const s = formGridStyle({ minItemWidth: 256, gutter: 24 })
    expect(s.columnGap).toBe(24)
    expect(s.rowGap).toBe(0)
  })
})

describe('FormGrid 渲染', () => {
  it('渲染成 display:grid 的容器，子节点全部保留', () => {
    renderWithProviders(
      <FormGrid>
        <span>字段一</span>
        <span>字段二</span>
      </FormGrid>,
    )

    const grid = screen.getByTestId('form-grid')
    expect(grid).toHaveStyle({ display: 'grid' })
    // 子节点**不被包一层 wrapper**：这是刻意的（本仓库有 12–13 个 DOM 结构敏感的测试文件，
    // 每多一层 div 就多一次 `closest()` 走空的机会）。
    expect(grid.children).toHaveLength(2)
    expect(screen.getByText('字段一')).toBeInTheDocument()
    expect(screen.getByText('字段二')).toBeInTheDocument()
  })

  it('minItemWidth 可覆盖默认下限（走的是 `??` 的另一条分支）', () => {
    // 只钉"覆盖这条路径能跑通"。具体数字（默认 256 = 96 + 160）已在
    // `formGridTemplate` 与 `useFormMetrics` 两处各自钉住，
    // 不在这里第三次写 256——同一个数字写三遍，改的时候必然漏一处。
    renderWithProviders(<FormGrid minItemWidth={480}>x</FormGrid>)
    expect(screen.getByTestId('form-grid')).toHaveStyle({ display: 'grid' })
  })

  it('调用方传的 style 与栅格默认值合并，且可以覆盖默认值', () => {
    renderWithProviders(
      <FormGrid style={{ display: 'flex' }}>x</FormGrid>,
    )
    // display 是 jsdom 一定认的属性；columnGap / rowGap 认不了，故那两项在纯函数层断言。
    expect(screen.getByTestId('form-grid')).toHaveStyle({ display: 'flex' })
  })
})
