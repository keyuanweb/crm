import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import FormModal from './FormModal'
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
