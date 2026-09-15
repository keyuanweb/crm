import { describe, expect, it, vi } from 'vitest'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { DatePicker, Form, Input, Select } from 'antd'
import { renderWithProviders } from '../../test/renderWithProviders'
import FormModal from './FormModal'
import { shouldSubmitOnEnter } from './formModalEnter'
import { FORM_MODAL_WIDTHS } from './formModalSize'

/**
 * 表单弹窗原语（088 交付物 2）。
 *
 * <p>钉的是**那 5 项现状里各漏一件的契约**（实测：`width` 32 个没设、`cancelText` 0/70、
 * `confirmLoading` 缺 23、`okText` 缺 10 —— 见 research.md §2.4）。
 * 它们单看都很小，合起来就是"同一个契约在 58 处各写一遍、每次都漏一项"。
 *
 * <p>**`destroyOnClose` 刻意不在这里断言**：它只在"关闭后再打开"时才可观察，
 * 而为了断言它去写一个开关弹窗的测试，测到的是 antd 的行为不是本组件的。
 * 该属性的作用是"防后人把拼写改成 5.25 的 `destroyOnHidden`"（5.22.0 静默忽略未知 prop），
 * 那件事靠源码里的注释 + `research.md` §2.4 的记录把关，靠测不出来。
 */
function modalWidth(): string {
  const el = document.querySelector('.ant-modal') as HTMLElement | null
  return el?.style.width ?? ''
}

describe('FormModal 宽度档位', () => {
  it('默认 md = 640（现状 13 处已在用的事实验证默认值）', () => {
    renderWithProviders(<FormModal open>x</FormModal>)
    expect(modalWidth()).toBe('640px')
  })

  it('四档宽度各自成立，且不再有第 11 种取值', () => {
    expect(FORM_MODAL_WIDTHS).toEqual({ sm: 480, md: 640, lg: 800, xl: 960 })
  })

  it('size="sm" 走 480', () => {
    renderWithProviders(
      <FormModal open size="sm">
        x
      </FormModal>,
    )
    expect(modalWidth()).toBe('480px')
  })

  it('显式 width 覆盖 size（留给确实不在四档之内的场景，如 90vw）', () => {
    renderWithProviders(
      <FormModal open size="xl" width="90vw">
        x
      </FormModal>,
    )
    expect(modalWidth()).toBe('90vw')
  })
})

describe('FormModal 脚注契约', () => {
  it('okText 默认「保存」、cancelText 默认「取消」（现状 cancelText 0/70，全是 antd 默认的 Cancel）', () => {
    renderWithProviders(<FormModal open>x</FormModal>)

    expect(screen.getByRole('button', { name: 'common.button.save' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'common.button.cancel' })).toBeInTheDocument()
  })

  it('两项文案都可覆盖（例如"创建"而不是"保存"）', () => {
    renderWithProviders(
      <FormModal open okText="创建" cancelText="返回">
        x
      </FormModal>,
    )

    // ⚠️ 正则里的 `\s*` 不是写得松：antd 的 Button 会在**恰好两个汉字**之间自动插一个空格
    // （`autoInsertSpace`，默认开启），所以 DOM 里是「创 建」而不是「创建」。
    // 写成精确字符串会在 antd 改这个默认值时莫名其妙地红，而它跟本组件的契约毫无关系。
    expect(screen.getByRole('button', { name: /创\s*建/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /返\s*回/ })).toBeInTheDocument()
  })

  it('未传 onSubmit 时点确定是空操作：不进入 loading（也不抛错）', () => {
    renderWithProviders(<FormModal open>x</FormModal>)

    const ok = screen.getByRole('button', { name: 'common.button.save' })
    fireEvent.click(ok)
    expect(ok).not.toHaveClass('ant-btn-loading')
  })
})

describe('FormModal 提交态', () => {
  it('onSubmit 未 resolve 前 OK 按钮一直 loading，resolve 后复位', async () => {
    let finish: () => void = () => {}
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    renderWithProviders(
      <FormModal open onSubmit={onSubmit}>
        x
      </FormModal>,
    )

    const ok = screen.getByRole('button', { name: 'common.button.save' })
    expect(ok).not.toHaveClass('ant-btn-loading')

    fireEvent.click(ok)
    // 现状 23 个表单弹窗缺 confirmLoading：提交期间按钮可重复点，
    // 于是"重复提交"这条老问题的根就在这里。
    await waitFor(() => expect(ok).toHaveClass('ant-btn-loading'))

    finish()
    await waitFor(() => expect(ok).not.toHaveClass('ant-btn-loading'))
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('onSubmit 抛错时 loading 也会复位（finally 而不是 then）', async () => {
    // 调用方在自己的 onSubmit 里消化异常（全库约定：message.error(extractErrorMessage(err))）。
    // 这里模拟的正是那种写法——`handleOk` 只负责 loading 生命周期，**不吞异常**，
    // 所以本用例不去构造"未被接住的 rejection"（那会让 vitest 报 unhandled rejection）。
    const onRejected = vi.fn()
    const onSubmit = vi.fn(async () => {
      try {
        throw new Error('保存失败')
      } catch (err) {
        onRejected(err)
      }
    })
    renderWithProviders(
      <FormModal open onSubmit={onSubmit}>
        x
      </FormModal>,
    )

    const ok = screen.getByRole('button', { name: 'common.button.save' })
    fireEvent.click(ok)

    await waitFor(() => expect(onRejected).toHaveBeenCalledTimes(1))
    // 不复位的话弹窗会永久卡在 loading，用户连重试都点不了。
    await waitFor(() => expect(ok).not.toHaveClass('ant-btn-loading'))
  })
})

/**
 * Enter 提交 —— 2026-09-15 用户对 plan 第 4 项的裁决：**只加 Enter，不改 footer**。
 *
 * <p>这一组里**「不提交」的三条比「提交」那一条更要紧**：Enter 在 TextArea 里是换行、
 * 在下拉控件里是确认选项，全局绑定的风险全在这三处误触上。只测「Enter 能提交」
 * 会把一个「到处误提交」的实现判成绿的。
 *
 * <p>## 定向破坏留痕（2026-09-15，逐个做、逐个逐字节还原）
 *
 * 护栏不证伪就等于没护栏，故逐条破坏过。**第 ① 条直接改掉了实现**——它暴露出的
 * 不是用例的问题，是代码的问题：
 *
 * | 破坏 | 结果 | 结论 |
 * |---|---|---|
 * | ① 把 `tagName === 'TEXTAREA'` 排成恒不成立 | **17 条全绿** | 那条守卫是**死分支**：末尾 `tagName === 'INPUT'` 已经兜住 TEXTAREA。**已删除该行**，理由并入末行注释 |
 * | ② 把 `.ant-select, .ant-picker` 排成恒不匹配 | **红 3 条** | 正是 DatePicker / Select / 判据表三条 —— 该守卫承重 |
 * | ③ 撤掉包裹 `div`、把 `onKeyDown` 挂回 `<Modal>` | **红 2 条**，且报 `got 0 times` | `onKeyDown` 在 `<Modal>` 上**一次都不触发**，坐实了「antd 静默丢 prop」不是读源码的臆测 |
 * | ④ 把末行 `tagName === 'INPUT'` 改成恒真 | **红 2 条** | 「TextArea 不提交」这条**不是空过**，它由末行单独承重 |
 */
describe('FormModal Enter 提交', () => {
  function setup(onSubmit = vi.fn()) {
    renderWithProviders(
      <FormModal open onSubmit={onSubmit}>
        <Form layout="vertical">
          <Form.Item name="title" label="标题">
            <Input placeholder="单行" />
          </Form.Item>
          <Form.Item name="content" label="正文">
            <Input.TextArea placeholder="多行" />
          </Form.Item>
          <Form.Item name="date" label="日期">
            <DatePicker placeholder="日期" />
          </Form.Item>
          <Form.Item name="owner" label="负责人">
            <Select showSearch placeholder="负责人" options={[{ value: 'a', label: 'A' }]} />
          </Form.Item>
        </Form>
      </FormModal>,
    )
    return onSubmit
  }

  it('在单行输入里按 Enter 提交（本项要加的就是这条）', () => {
    const onSubmit = setup()
    fireEvent.keyDown(screen.getByPlaceholderText('单行'), { key: 'Enter' })
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it('在 TextArea 里按 Enter **不**提交——那是换行', () => {
    const onSubmit = setup()
    fireEvent.keyDown(screen.getByPlaceholderText('多行'), { key: 'Enter' })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('在 DatePicker 的输入框里按 Enter **不**提交——那是确认日期', () => {
    const onSubmit = setup()
    fireEvent.keyDown(screen.getByPlaceholderText('日期'), { key: 'Enter' })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('在 Select 的搜索框里按 Enter **不**提交——那是确认选项', () => {
    const onSubmit = setup()
    const search = document.querySelector('.ant-select-selection-search-input')
    // 自证锚点：取不到就说明选择器过期了，本条必须**红**而不是空过。
    expect(search).not.toBeNull()
    fireEvent.keyDown(search as Element, { key: 'Enter' })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('非 Enter 键不提交', () => {
    const onSubmit = setup()
    fireEvent.keyDown(screen.getByPlaceholderText('单行'), { key: 'a' })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('没给 onSubmit 时 Enter 是空操作（与 OK 按钮一致，不抛错）', () => {
    renderWithProviders(
      <FormModal open>
        <Input placeholder="单行" />
      </FormModal>,
    )
    expect(() => fireEvent.keyDown(screen.getByPlaceholderText('单行'), { key: 'Enter' })).not.toThrow()
  })

  it('提交期间再按 Enter 不会二次提交', async () => {
    let finish: () => void = () => {}
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    setup(onSubmit)
    const input = screen.getByPlaceholderText('单行')

    fireEvent.keyDown(input, { key: 'Enter' })
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))

    // 第一次 Enter 之后 `submitting` 已置位（React 对 keydown 这类离散事件会同步冲刷状态），
    // 所以这里必须仍是 1 次。少了 `submitting` 这道闸，连按两下回车就提交两遍。
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onSubmit).toHaveBeenCalledTimes(1)

    finish()
  })
})

/**
 * 判据真值表。上面那一组走的是真实 antd 控件，这组走的是最小 DOM——
 * 两者互补：这组能在 antd 改类名时立刻指出是哪一格失了守。
 */
describe('shouldSubmitOnEnter 判据表', () => {
  const fake = (key: string, target: unknown) => ({ key, target }) as unknown as ReactKeyboardEvent<HTMLElement>

  it('只有「真按了 Enter」且「落在单行输入上」才为真', () => {
    const input = document.createElement('input')
    const textarea = document.createElement('textarea')
    const button = document.createElement('button')
    const picker = document.createElement('div')
    picker.className = 'ant-picker'
    const inputInPicker = document.createElement('input')
    picker.appendChild(inputInPicker)
    const select = document.createElement('div')
    select.className = 'ant-select'
    const inputInSelect = document.createElement('input')
    select.appendChild(inputInSelect)

    expect(shouldSubmitOnEnter(fake('Enter', input))).toBe(true)
    expect(shouldSubmitOnEnter(fake('a', input))).toBe(false)
    expect(shouldSubmitOnEnter(fake('Enter', textarea))).toBe(false)
    expect(shouldSubmitOnEnter(fake('Enter', button))).toBe(false)
    expect(shouldSubmitOnEnter(fake('Enter', inputInPicker))).toBe(false)
    expect(shouldSubmitOnEnter(fake('Enter', inputInSelect))).toBe(false)
  })
})
