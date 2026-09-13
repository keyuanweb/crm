import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import {
  LABEL_WIDTH,
  MIN_FIELD_WIDTH,
  labelWidthFor,
  useFormMetrics,
  type FormMetrics,
} from './useFormMetrics'

/**
 * 表单度量（088 交付物 2）。
 *
 * <p>分两层钉：
 *
 * <p>① **语言分支走纯函数**。测试环境把 `react-i18next` 全局打桩成 `language: 'zh'`
 * （`src/test/setup.ts:115`，72 个测试文件共享），英文分支在 hook 里**测不到**；
 * 抽成 `labelWidthFor` 后可以逐值断言，而 hook 自己不再含语言分支。
 *
 * <p>② **hook 只剩"覆盖值优先"这一条分支**，用探针组件把两个字段的两种来源都跑到
 * （传参 / 不传参）。留一条没跑过的分支在这里，正是本批次"新组件压穿 functions 覆盖率"
 * 风险里最廉价的那一半。
 */
describe('labelWidthFor', () => {
  it('英文取 112，中文取 96', () => {
    expect(labelWidthFor('en')).toBe(LABEL_WIDTH.en)
    expect(labelWidthFor('zh-CN')).toBe(LABEL_WIDTH.zh)
  })

  it('带地区的英文标签（en-US / en-GB）同样走英文分支', () => {
    // 判据是 startsWith('en')，与 LocaleProvider.tsx:23 同一写法——
    // 若这里改成 === 'en'，en-US 会悄悄退回 96px 并把 "Contact Person" 折成两行。
    expect(labelWidthFor('en-US')).toBe(112)
    expect(labelWidthFor('en-GB')).toBe(112)
  })

  it('语言缺失或为空时退回中文宽度，而不是 NaN / undefined', () => {
    expect(labelWidthFor(undefined)).toBe(LABEL_WIDTH.zh)
    expect(labelWidthFor('')).toBe(LABEL_WIDTH.zh)
  })

  it('英文宽度确实大于中文宽度（这条是"按语言取值"本身的证据）', () => {
    expect(LABEL_WIDTH.en).toBeGreaterThan(LABEL_WIDTH.zh)
  })
})

/** 探针：把 hook 的返回值渲染成文本，绕过"hook 只能在组件里调"的限制。 */
function Probe(props: { labelWidth?: number; minFieldWidth?: number }) {
  const m: FormMetrics = useFormMetrics(
    props.labelWidth === undefined && props.minFieldWidth === undefined
      ? undefined
      : { labelWidth: props.labelWidth, minFieldWidth: props.minFieldWidth },
  )
  return <span data-testid="metrics">{`${m.labelWidth}/${m.minItemWidth}`}</span>
}

describe('useFormMetrics', () => {
  it('不传覆盖值时：中文标签 96 + 输入框下限 160 = 栅格下限 256', () => {
    renderWithProviders(<Probe />)
    expect(screen.getByTestId('metrics')).toHaveTextContent(`${LABEL_WIDTH.zh}/256`)
  })

  it('传覆盖值时以覆盖值为准（两个字段分别独立生效）', () => {
    renderWithProviders(<Probe labelWidth={140} minFieldWidth={200} />)
    expect(screen.getByTestId('metrics')).toHaveTextContent('140/340')
  })

  // 下面两条拆成独立用例而不是在同一条里渲染两次：`getByTestId` 遇到**重复**即抛错，
  // 而两次 `render` 会把两个探针都留在 document 上（本仓库的 `setup.ts` 未配 cleanup 之外的
  // 特殊处理，依赖这一点会让断言变成"看运气"）。
  it('只覆盖 minFieldWidth 时，labelWidth 仍走默认', () => {
    renderWithProviders(<Probe minFieldWidth={200} />)
    expect(screen.getByTestId('metrics')).toHaveTextContent(`${LABEL_WIDTH.zh}/296`)
  })

  it('只覆盖 labelWidth 时，minFieldWidth 仍走默认', () => {
    renderWithProviders(<Probe labelWidth={140} />)
    expect(screen.getByTestId('metrics')).toHaveTextContent(`140/${140 + MIN_FIELD_WIDTH}`)
  })
})
