import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import DepartmentDetail from './DepartmentDetail'
import type { Department } from '../types/department'

/**
 * 095 T035 · `068` 的 T035 明文点名了这个文件，而它此前**没有被测对象** ——
 * 详情体是写在页面文件里的内联 JSX。T030 把详情抽成 `components/DepartmentDetail`
 * 就是为了让这条任务成立（两者是硬依赖，见该组件的文件头）。
 *
 * ## 读数口径
 *
 * `setup.ts` 把 `react-i18next` 换成 mock，`t(key)` **返回 key 本身**（缺键抛错）。
 * 故这里断言的是 `key：值` 这种**拼接结果**，而不是中文文案 ——
 * 这与「`i18n:check` 双向比对保证键齐」是互补的两件事：前者管键在不在，
 * 这里管**值有没有落到那一行上**。
 *
 * 本组件是**纯展示**（不取数、无状态），所以不需要 mock service，也不需要 await。
 */
const full: Department = {
  id: 7,
  name: '华东销售部',
  parentId: 3,
  description: '华东大区',
  sortOrder: 5,
  createdAt: '2026-01-02T03:04:05Z',
  createdBy: 'admin',
  memberCount: 12,
  childCount: 2,
  version: 1,
  children: [],
}

/** 只有必填字段的行 —— 用来验每一项的回落值。 */
const minimal: Department = { id: 9, name: '新部门', version: 1, children: [] }

const text = () => document.body.textContent ?? ''
/** 某一行的完整拼接（label 用的是 key，故直接拼 key）。 */
const rowText = (key: string) => `pages.departmentList.${key}：`

describe('DepartmentDetail（095 T035）', () => {
  it('九项字段逐一亮出，标签与值成对出现', () => {
    renderWithProviders(<DepartmentDetail department={full} parentLabel="总公司  " />)

    expect(text()).toContain(rowText('detailName') + '华东销售部')
    expect(text()).toContain(rowText('detailId') + '7')
    expect(text()).toContain(rowText('detailParent') + '总公司  ')
    expect(text()).toContain(rowText('detailDesc') + '华东大区')
    expect(text()).toContain(rowText('detailSort') + '5')
    expect(text()).toContain(rowText('detailMembers') + '12')
    expect(text()).toContain(rowText('detailChildren') + '2')
    expect(text()).toContain(rowText('detailCreatedBy') + 'admin')
  })

  it('创建时间按 zh-CN 本地化展示（不是 ISO 原串）', () => {
    renderWithProviders(<DepartmentDetail department={full} />)

    expect(text()).toContain(rowText('detailCreatedAt'))
    // 不写死格式化结果（随运行环境时区而变），只断言：年份在场、且**不是**原样 ISO 串。
    expect(text()).toContain('2026')
    expect(text()).not.toContain('2026-01-02T03:04:05Z')
  })

  it('不传 parentLabel 时显示「顶级」而非空白', () => {
    const { unmount } = renderWithProviders(<DepartmentDetail department={{ ...full, parentId: undefined }} />)

    expect(text()).toContain(rowText('detailParent') + 'pages.departmentList.detailTopLevel')
    unmount()
  })

  it('传了 parentLabel 就显示传入值（不被「顶级」顶掉）', () => {
    // ⚠️ 两个方向的取舍都验：页面侧由 `parentId ? 查表 : undefined` 决定传不传，
    // 组件本身只负责「有则显示、无则顶级」——把判据放在组件里会被页面的取值掩盖。
    const { unmount } = renderWithProviders(<DepartmentDetail department={full} parentLabel="总公司" />)

    expect(text()).toContain(rowText('detailParent') + '总公司')
    expect(text()).not.toContain(rowText('detailParent') + 'pages.departmentList.detailTopLevel')
    unmount()
  })

  it('缺字段回落：文本字段给 -，计数字段给 0（不是 undefined/NaN）', () => {
    renderWithProviders(<DepartmentDetail department={minimal} />)

    expect(text()).toContain(rowText('detailDesc') + '-')
    expect(text()).toContain(rowText('detailCreatedAt') + '-')
    expect(text()).toContain(rowText('detailCreatedBy') + '-')
    expect(text()).toContain(rowText('detailSort') + '0')
    expect(text()).toContain(rowText('detailMembers') + '0')
    expect(text()).toContain(rowText('detailChildren') + '0')
    expect(text()).not.toContain('undefined')
    expect(text()).not.toContain('NaN')
  })

  it('空描述与 0 计数**不**被回落混淆：description 有值时优先于 -', () => {
    renderWithProviders(<DepartmentDetail department={{ ...minimal, description: '0' }} />)

    // `description || '-'` 对字符串 '0' 是真值 ⇒ 仍显示 '0'，不被回落吃掉
    expect(text()).toContain(rowText('detailDesc') + '0')
  })

  it('九行真是九行：每行一个 <strong> 标签，不多不少', () => {
    renderWithProviders(<DepartmentDetail department={full} />)

    // 以「标签以全角冒号收尾」定位（RTL 的 getNodeText 只取**直接文本子节点**，
    // 故这九条正好落在各自的 <strong> 上，不会把外层的 div 也算进来）。
    const labels = screen.getAllByText(/：$/)
    expect(labels.length).toBe(9)
    for (const el of labels) {
      expect(el.tagName).toBe('STRONG')
    }
  })
})
