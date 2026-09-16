import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomObjectRecordPage from './CustomObjectRecordPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/customObjectService', () => ({
  fetchCustomObjects: vi.fn(),
  fetchObjectRecords: vi.fn(),
  createObjectRecord: vi.fn(),
  updateObjectRecord: vi.fn(),
  deleteObjectRecord: vi.fn(),
}))

/**
 * 096 权限收口 · `CustomObjectRecordPage` 的**三个写码**渲染测试。
 *
 * <p>本页 096 之前**整页零判权**：新建按钮、编辑链接、删除链接无条件渲染。后端那三个端点
 * 当时是 `@PreAuthorize("hasAnyRole('ADMIN','SALES')")`，096 改为 `custom_object_record:create`
 * / `:update` / `:delete` 三个**不同的**码（补授范围 = 原门放行的集合，`V90`）。
 *
 * <p>三码各自独立，所以本文件用**交叉断言**钉死：只持 create 的人看不见编辑与删除、
 * 只持 update 的看不见新建与删除…… 哪天有人把三个判据合并成一个码，第 4–6 例会立刻红。
 *
 * <p>第 3 例单独守一条更细的判据：**读码不放行写**。`custom_object_record:read` 是本页取数
 * （列表 + 详情）用的码，它**不在** `usePerms` 的申请列表里；持它而持写码的人必须三个控件全不可见。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts`），拿管理员永远测不出「看不见」。
 */
const customObjectRow = {
  id: 1,
  name: '设备档案',
  code: 'EQUIPMENT',
  fields: [{ field: 'name', label: '名称', type: 'TEXT' as const, required: true }],
  enabled: true,
  version: 0,
}

const recordRow = {
  id: 7,
  objectId: 1,
  values: { name: '空压机 A' },
  createdAt: '2026-09-16T10:00:00',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const withPerms = (codes: string[]): UserInfo => ({ ...salesNoPerm, permissions: codes })

const queryCreate = () => screen.queryByText('pages.customObject.btnAddRecord')
const queryEdit = () => screen.queryByText('pages.customObject.edit')
const queryDelete = () => screen.queryByText('pages.customObject.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCustomObjects, fetchObjectRecords } = await import('../../services/customObjectService')
  vi.mocked(fetchCustomObjects).mockResolvedValue({
    items: [customObjectRow],
    total: 1,
    page: 1,
    pageSize: 100,
  } as never)
  vi.mocked(fetchObjectRecords).mockResolvedValue({
    items: [recordRow],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(
    <Routes>
      <Route path="/custom-objects/:id/records" element={<CustomObjectRecordPage />} />
    </Routes>,
    { route: '/custom-objects/1/records' },
  )
  // 反空洞守卫：等到表格真的渲染出这一行与那条记录，否定断言才有意义。
  await screen.findByText('设备档案 · pages.customObject.records')
  await screen.findByText('空压机 A')
}

describe('CustomObjectRecordPage 三个写码（096）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：新建、编辑、删除都可见', async () => {
    await renderPage(adminUser)

    expect(queryCreate()).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
  })

  it('② 零权限码的 SALES：三者都不可见（页面与记录行本身照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：页面渲染好了（返回列表按钮 + 数据行都在），
    // 不见的确实只是那三个受权限管辖的控件。
    expect(screen.getByText('pages.customObject.backToList')).toBeInTheDocument()
    expect(screen.getByText('空压机 A')).toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
    expect(queryEdit()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('③ 只持**读**码（custom_object_record:read）的 SALES：仍然三者都不可见（读码不放行写）', async () => {
    await renderPage(withPerms(['custom_object_record:read']))

    expect(screen.getByText('空压机 A')).toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
    expect(queryEdit()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('④ 只持 custom_object_record:create：看得见新建，看不见编辑与删除', async () => {
    await renderPage(withPerms(['custom_object_record:create']))

    expect(queryCreate()).toBeInTheDocument()
    // 交叉断言：三个码不得互相放行
    expect(queryEdit()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('⑤ 只持 custom_object_record:update：看得见编辑，看不见新建与删除', async () => {
    await renderPage(withPerms(['custom_object_record:update']))

    expect(queryEdit()).toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('⑥ 只持 custom_object_record:delete：看得见删除，看不见新建与编辑', async () => {
    await renderPage(withPerms(['custom_object_record:delete']))

    expect(queryDelete()).toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
    expect(queryEdit()).not.toBeInTheDocument()
  })
})
