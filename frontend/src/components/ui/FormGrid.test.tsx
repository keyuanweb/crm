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
  const base = { minItemWidth: 256, gutter: 16 }

  it('默认（不传 cols）走 auto-fit，下限是 min(100%, 栅格宽度)', () => {
    const t = formGridTemplate(base)
    expect(t).toBe('repeat(auto-fit, minmax(min(100%, 256px), 1fr))')
    // 反假绿：这个 min() 不能省。省掉后宽容器下看不出任何差别，窄屏才溢出。
    expect(t).toContain('min(100%,')
  })

  it('用 auto-fit 而不是 auto-fill——否则最后一行会留一条死列，3 个子项撑不满', () => {
    expect(formGridTemplate(base)).toContain('auto-fit')
    expect(formGridTemplate(base)).not.toContain('auto-fill')
  })

  it('显式 cols 时用 repeat(N, minmax(0, 1fr))，且下限为 0（允许子项被压缩）', () => {
    expect(formGridTemplate({ ...base, cols: 3 })).toBe('repeat(3, minmax(0, 1fr))')
  })

  it('显式 cols 的模板里不再出现 min(100%) —— 两套下限不能混用', () => {
    expect(formGridTemplate({ ...base, cols: 2 })).not.toContain('min(100%')
  })

  it('栅格宽度会被原样写进模板（不是被硬编码成 256）', () => {
    expect(formGridTemplate({ minItemWidth: 480, gutter: 16 })).toContain('480px')
  })
})

/**
 * 列数上限（088 T043，页面级表单）。
 *
 * <p>这里**不满足于断言模板串**：上限的全部意义是"在真实容器宽度下最多排 N 列"，
 * 所以另有一条**按 CSS 自己的轨道数公式反算**的算术核对
 * （`k = floor((W + g) / (T + g))`，`T` 由被测函数给出的模板串解析而来），
 * 扫一遍 320–1920 的宽度区间断言 `k ≤ 3`，并在几个真会遇到的宽度上钉住确切列数。
 * 只钉字符串的话，"把上限写成 `cols={3}`"这种**退化**照样能过前一条断言，
 * 却在窄容器上把字段压成 100px——那正是本组件要治的病。
 */
describe('formGridTemplate · maxCols', () => {
  const gutter = 16
  const minItemWidth = 200 // = VERTICAL_MIN_ITEM_WIDTH，页面级表单用的下限

  /**
   * 从模板串里把轨道下限 `T`（px）解析出来；`W` 由调用方给出。
   * 两种形态都能解：带上限的 `max(Npx, calc(…))` 与不带上限的裸 `min(100%, Npx)`。
   */
  const trackOf = (template: string, containerWidth: number): number => {
    const m = /max\((\d+)px, calc\(\(100% \+ (\d+)px\) \/ (\d+) - (\d+)px \+ (\d+)px\)\)/.exec(
      template,
    )
    if (m) {
      const [, floorPx, gapPx, divisor, subPx, epsPx] = m
      expect(Number(gapPx)).toBe(gutter)
      const cap = (containerWidth + Number(gapPx)) / Number(divisor) - Number(subPx) + Number(epsPx)
      return Math.max(Number(floorPx), cap)
    }
    const plain = /minmax\(min\(100%, (\d+)px\)/.exec(template)
    expect(plain, `模板里没有可解析的轨道下限：${template}`).not.toBeNull()
    return Number((plain as RegExpExecArray)[1])
  }

  /** CSS 的轨道数 = 能放下的最大轨道数（`repeat(auto-fit, …)` 的定义）。 */
  const colsAt = (template: string, containerWidth: number): number => {
    // `min(100%, …)` 那层截断：容器比轨道下限还窄时退化成一列铺满。
    const track = Math.min(containerWidth, trackOf(template, containerWidth))
    return Math.max(1, Math.floor((containerWidth + gutter) / (track + gutter)))
  }

  const capped = formGridTemplate({ minItemWidth, maxCols: 3, gutter })

  it('仍然是 auto-fit + min(100%)——上限不得退化成写死列数', () => {
    expect(capped).toContain('auto-fit')
    expect(capped).toContain('min(100%,')
    expect(capped).not.toMatch(/repeat\(3,/)
  })

  it('轨道下限取「控件下限」与「上限表达式」的较大者，余量 1px 写在表达式中', () => {
    expect(capped).toBe(
      'repeat(auto-fit, minmax(min(100%, max(200px, calc((100% + 16px) / 4 - 16px + 1px))), 1fr))',
    )
  })

  it('宽度区间 320–1920 内，列数恒 ≤ 3，且列数随宽度单调不减', () => {
    let prev = 0
    for (let w = 320; w <= 1920; w += 4) {
      const k = colsAt(capped, w)
      expect(k, `容器 ${w}px 排出了 ${k} 列`).toBeLessThanOrEqual(3)
      expect(k).toBeGreaterThanOrEqual(prev)
      prev = k
    }
  })

  it('在真会遇到的容器宽度上钉住确切列数', () => {
    // 页面级卡片内 ≈984（1280 视口）→ 3 列；1920 视口 ≈1440 → 仍 3 列（上限生效处）
    expect(colsAt(capped, 984)).toBe(3)
    expect(colsAt(capped, 1440)).toBe(3)
    // 窄容器：上限不该咬（咬了就说明它退化成了写死列数）
    expect(colsAt(capped, 520)).toBe(2)
    expect(colsAt(capped, 404)).toBe(1)
    // 切换点：上限恰好与控件下限相等处，两侧连续（3 列，不出现断口）
    expect(colsAt(capped, 844)).toBe(3)
    expect(colsAt(formGridTemplate({ minItemWidth, gutter }), 844)).toBe(3)
  })

  it('不设上限时行为逐字节不变（上限是纯增量）', () => {
    expect(formGridTemplate({ minItemWidth, gutter })).toBe('repeat(auto-fit, minmax(min(100%, 200px), 1fr))')
  })

  it('cols 优先：已有「恰好 N 列」的定论时上限无意义', () => {
    expect(formGridTemplate({ cols: 2, minItemWidth, maxCols: 3, gutter })).toBe('repeat(2, minmax(0, 1fr))')
  })

  it('0 / 负数 / 小数一律按「未传」处理，而不是排出一列或 NaN', () => {
    const plain = formGridTemplate({ minItemWidth, gutter })
    for (const bad of [0, -1, 2.5, Number.NaN]) {
      expect(formGridTemplate({ minItemWidth, maxCols: bad, gutter })).toBe(plain)
    }
  })

  it('maxCols=1 合法（单列铺满），且与 cols=1 不是一回事', () => {
    expect(colsAt(formGridTemplate({ minItemWidth, maxCols: 1, gutter }), 984)).toBe(1)
    // cols=1 是写死单列（模板不同）；maxCols=1 仍是 auto-fit
    expect(formGridTemplate({ minItemWidth, maxCols: 1, gutter })).toContain('auto-fit')
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
