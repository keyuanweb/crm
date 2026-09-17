import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Form } from 'antd'
import { renderWithProviders } from '../test/renderWithProviders'
import { CustomFieldFormItems } from './CustomFieldItems'
import type { CustomField } from '../types/customField'

vi.mock('../services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(),
}))

/**
 * 103 · `CustomFieldFormItems` 消费字段权限标记（`permission`）。
 *
 * <p><b>为什么必须有这个文件</b>：后端那份掩码与回补做得再对，只要表单把 READ_ONLY 字段渲染成
 * 一个随手就能改的输入框、或者把 HIDDEN 字段也渲染出来，用户看到的就完全是另一回事——而这一面
 * <b>没有任何后端用例看着</b>，它全在浏览器里。
 *
 * <p>四条断言各对应一处真实改动：
 * <ol>
 *   <li>HIDDEN 字段<b>整个不渲染</b>（连标签都不出现）——否则必填的 HIDDEN 字段会变成一个用户
 *       填不了的必填空项，创建路径上还让用户往看不见的字段里打字（后端 validateWrite ⇒ 422
 *       `FIELD_HIDDEN`）；
 *   <li>READ_ONLY 字段渲染、但控件 `disabled`；
 *   <li>EDITABLE 字段两者皆非（<b>反空洞</b>：否则"全都 disabled"也能让第 2 条绿）；
 *   <li>⚠️ <b>disabled 的控件仍随表单提交其值</b>——这一条看着最反直觉，却是整个回传路径的地基：
 *       antd 的 `Form` 把值存在自己的 rc-field-form store 里，`onFinish` 从那里取值，
 *       与原生 HTML 表单"disabled 即不提交"<b>相反</b>。若这条不成立，READ_ONLY 字段的值根本
 *       回不到后端，`validateWrite` 的「未变更」判定与必填校验会双双失效。
 * </ol>
 */
const HIDDEN_ID = 901
const READ_ONLY_ID = 902
const EDITABLE_ID = 903

function def(id: number, name: string, permission?: CustomField['permission']): CustomField {
  return {
    id,
    entityType: 'LEAD',
    name,
    fieldType: 'TEXT',
    required: false,
    enabled: true,
    version: 0,
    permission,
  }
}

const definitions = [
  def(HIDDEN_ID, '隐藏字段', { hidden: true, readOnly: false }),
  def(READ_ONLY_ID, '只读字段', { hidden: false, readOnly: true }),
  def(EDITABLE_ID, '可编辑字段', { hidden: false, readOnly: false }),
]

async function mockDefinitions() {
  const svc = await import('../services/customFieldService')
  vi.mocked(svc.fetchFieldDefinitions).mockResolvedValue(definitions as never)
}

/** 渲染表单项并等它真的挂出来（反空洞守卫：不等就可能是在"还没加载"上成立的断言）。 */
async function renderItems() {
  await mockDefinitions()
  renderWithProviders(
    <Form>
      <CustomFieldFormItems entityType="LEAD" />
    </Form>,
  )
  await screen.findByLabelText('只读字段')
}

describe('CustomFieldFormItems 的字段权限标记（103）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('HIDDEN 字段不渲染：标签与控件都不出现', async () => {
    await renderItems()

    expect(screen.queryByText('隐藏字段')).toBeNull()
    expect(screen.queryByLabelText('隐藏字段')).toBeNull()
    // 反空洞：同一棵树上别的字段确实渲染出来了，否则上两条会因为"整个表单是空的"而假绿
    expect(screen.getByLabelText('只读字段')).toBeInTheDocument()
  })

  it('READ_ONLY 字段渲染，但控件 disabled', async () => {
    await renderItems()

    expect(screen.getByLabelText('只读字段')).toBeDisabled()
  })

  it('EDITABLE 字段的控件不 disabled（反空洞：不是"全都禁用"）', async () => {
    await renderItems()

    expect(screen.getByLabelText('可编辑字段')).not.toBeDisabled()
  })

  it('disabled 的 READ_ONLY 控件仍随表单提交其值（antd store 语义，回传路径靠它成立）', async () => {
    await mockDefinitions()
    const onFinish = vi.fn()
    renderWithProviders(
      <Form
        initialValues={{ customFieldValues: { [READ_ONLY_ID]: '只读原值' } }}
        onFinish={onFinish}
      >
        <CustomFieldFormItems entityType="LEAD" />
        <button type="submit">submit</button>
      </Form>,
    )
    await screen.findByLabelText('只读字段')

    fireEvent.click(screen.getByRole('button', { name: 'submit' }))

    await waitFor(() => expect(onFinish).toHaveBeenCalled())
    const values = onFinish.mock.calls[0][0] as unknown as {
      customFieldValues: Record<string, string>
    }
    expect(values.customFieldValues[String(READ_ONLY_ID)]).toBe('只读原值')
  })
})
