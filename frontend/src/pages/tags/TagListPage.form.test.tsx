import { describe, expect, it, vi, beforeEach } from 'vitest'
import { configure, screen, within, fireEvent, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import TagListPage from './TagListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 整页渲染型用例的两个上限（取值与实测依据见 `src/test/timeouts.ts`）：
// ① `findBy*`/`waitFor` 的等待上限——默认只等 1000ms，全量并发下不够用；
// ② 单条用例上限——仓库默认 20000ms，而本页每条要挂载 antd Modal + Form。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

vi.mock('../../services/tagService', () => ({
  fetchTags: vi.fn(),
  createTag: vi.fn(),
  updateTag: vi.fn(),
  deleteTag: vi.fn(),
}))

/**
 * 088 · `TagListPage` 样板转换（P2 T033）的行为层证据。
 *
 * <p>为什么这一页需要**新开一个文件**而不是扩 `.perm.test.tsx`：那个文件管的是 086 的
 * 权限语义（谁看得见「删除」），这里管的是 088 的 `FormModal` 契约——**受众不同、失效原因不同**。
 *
 * <p>为什么必须有这几条：`.perm.test.tsx` 的三个用例**从不打开弹窗**，于是
 * `FormModal` 的 `cancelText` 默认值、`onSubmit` 接线、`labelCol` 的宽度来源（`useFormMetrics`）
 * 在本页是**零执行**的——不做这一条，"弹窗改成 FormModal 之后还能不能开、还能不能提交"
 * 就只能靠肉眼。这一页是 `FormModal` 的**第一个 sm 档使用者**（480px 档），
 * 且它的表单是 P2 里最简单的一份，所以 API 若在此处不通，后面三页不必谈。
 *
 * <p>五条断言各自对应 T033 的一处真实改动：
 * ① 弹窗换 `FormModal`：标题/字段照旧，且**取消键吃到了默认值** `common.button.cancel`
 *    （改前是 antd 自己的英文 `Cancel`——本页从未传过 `cancelText`）；
 * ② 标签宽度改从 `useFormMetrics()` 取：`80px` → **96px**（中文档）。这是本页唯一
 *    **肉眼可见的尺寸变化**——标签栏比原先宽 16px，输入区相应变窄；
 * ③ 编辑弹窗的回填：`openEdit` 的 `setFieldsValue` 在 `destroyOnClose` 下仍要生效
 *    （先写 store、后挂载，靠 rc-field-form 的 `setInitialValues` 把 store 合并在
 *    `initialValues` **之后**）。这条钉的是**既有行为**，改 `Modal`→`FormModal` 最容易
 *    在这里静默坏掉；
 * ④ `onSubmit` 真的接到了 `onSave`：校验会拦住提交（改前是 `onOk={() => void onSave()}`）；
 * ⑤ 提交真的落到 service 上，且新建时 `entityType: 'CUSTOMER'` 是**页面补的**、不是表单字段。
 *
 * <p>用 ADMIN：本文件不涉及权限语义，而 `hasPerm` 对 ADMIN 直通，
 * 于是"行内删除链接在不在"不会变成干扰变量（行内「编辑」链接不受权限管辖，一直渲染）。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

const tagRow = { id: 1, name: '重点客户', color: 'red', entityType: 'CUSTOMER' }

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const svc = await import('../../services/tagService')
  vi.mocked(svc.fetchTags).mockResolvedValue([tagRow] as never)
  vi.mocked(svc.createTag).mockResolvedValue(undefined as never)
  vi.mocked(svc.updateTag).mockResolvedValue(undefined as never)
  renderWithProviders(<TagListPage />)
  // 等表格真的渲染出这一行再往下走 —— 否则后面的断言可能是在"页面还没加载出来"上成立的（假绿）。
  await screen.findByText('重点客户')
}

/** 点工具栏「新增标签」并返回弹窗。 */
async function openCreate() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.tagList\.btnAdd/ }))
  return await screen.findByRole('dialog')
}

/** 点行内「编辑」并返回弹窗。 */
async function openEdit() {
  fireEvent.click(screen.getByText('pages.tagList.edit'))
  return await screen.findByRole('dialog')
}

describe('TagListPage 的布局原语转换（088 P2 T033）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('弹窗换成 FormModal 后：仍能打开，取消键吃到了默认值，标签宽度改成 useFormMetrics 的 96px', async () => {
    await renderPage()
    const dialog = await openCreate()

    expect(within(dialog).getByText('pages.tagList.modalAddTitle')).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/pages\.tagList\.formNameLabel/)).toBeInTheDocument()
    expect(within(dialog).getByText('pages.tagList.formColorLabel')).toBeInTheDocument()

    // ① 页内**没有**传 cancelText，所以渲染出的键名只可能来自 FormModal 的默认值；
    //    改前这里是 antd 自带的英文 `Cancel`（中英文界面下都不对）。
    expect(within(dialog).getByText('common.button.cancel')).toBeInTheDocument()
    // OK 键的文案也保住了（防止"换 FormModal 时把 okText 丢了"退回 antd 默认的 OK）。
    expect(within(dialog).getByText('pages.tagList.btnSave')).toBeInTheDocument()

    // ② 标签宽度：antd 把 labelCol 落到 `.ant-form-item-label` 这个 Col 的 style 上
    //    （`node_modules/antd/lib/form/FormItemLabel.js` 末尾 `React.createElement(Col, {...mergedLabelCol, ...})`）。
    //    读 style **属性字符串**而不是 getComputedStyle：jsdom 的 cssstyle 不反映 flex 这类
    //    简写的计算结果，而属性字符串是 antd 直接写上去的，如实。
    const label = within(dialog).getByText('pages.tagList.formNameLabel')
    const labelColStyle = label.closest('.ant-form-item-label')?.getAttribute('style') ?? ''
    expect(labelColStyle).toContain('96px')
    expect(labelColStyle).not.toContain('80px')
  })

  it('点行内「编辑」时弹窗回填既有值（setFieldsValue 在 destroyOnClose 下仍生效）', async () => {
    await renderPage()
    const dialog = await openEdit()

    expect(within(dialog).getByText('pages.tagList.modalEditTitle')).toBeInTheDocument()
    // 回填本身没有测试覆盖过：`.perm.test.tsx` 从不打开弹窗，而这条路径在
    // "先写 store、后挂载 Form"这个次序上最脆弱。
    expect(within(dialog).getByLabelText(/pages\.tagList\.formNameLabel/)).toHaveValue('重点客户')
  })

  it('创建弹窗的 onSubmit 接到校验：必填项为空时不提交，并显示字段级错误', async () => {
    await renderPage()
    const { createTag } = await import('../../services/tagService')
    const dialog = await openCreate()

    // 「标签名」是唯一必填项，故意留空。
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.tagList\.btnSave/ }))

    // 正向信号：字段级错误出现了 —— 它证明 validateFields() 真的跑过并拒绝了。
    // 只断言"没调用 createTag"是不够的：那可能只是"还没来得及调用"（假绿）。
    expect(await within(dialog).findByText('pages.tagList.formNameRequired')).toBeInTheDocument()
    expect(createTag).not.toHaveBeenCalled()
  })

  it('创建提交落到 service：entityType 由页面补成 CUSTOMER', async () => {
    await renderPage()
    const { createTag } = await import('../../services/tagService')
    const dialog = await openCreate()

    fireEvent.change(within(dialog).getByLabelText(/pages\.tagList\.formNameLabel/), {
      target: { value: '战略客户' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.tagList\.btnSave/ }))

    await waitFor(() => expect(createTag).toHaveBeenCalledTimes(1))
    // 逐字段断言而不是 toHaveBeenCalledWith 整体比对：`color` 未填时值是 `undefined`，
    // 而 `{color: undefined}` 与 `{}` 是否相等取决于断言库对 undefined 键的宽容度
    // —— 那是个与本次改动无关的变量，不该混进来。
    const payload = vi.mocked(createTag).mock.calls[0][0]
    expect(payload.name).toBe('战略客户')
    expect(payload.entityType).toBe('CUSTOMER')
  })

  it('编辑提交落到 service：走的是 updateTag(id, values) 而不是 createTag', async () => {
    await renderPage()
    const { createTag, updateTag } = await import('../../services/tagService')
    const dialog = await openEdit()

    fireEvent.change(within(dialog).getByLabelText(/pages\.tagList\.formNameLabel/), {
      target: { value: '核心客户' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.tagList\.btnSave/ }))

    await waitFor(() => expect(updateTag).toHaveBeenCalledTimes(1))
    expect(vi.mocked(updateTag).mock.calls[0][0]).toBe(1)
    expect(vi.mocked(updateTag).mock.calls[0][1]).toMatchObject({ name: '核心客户' })
    // `editing` 分支没走错：改一版被当成新建是**静默的数据错误**。
    expect(createTag).not.toHaveBeenCalled()
  })
})
