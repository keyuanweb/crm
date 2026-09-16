import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import UserManagementPage from './UserManagementPage'
import * as userService from '../../services/userService'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

/**
 * 管理员重置某账号的双因素认证（082，FR-M10，契约 §7）。
 *
 * <p>这个动作的语义是**拆除**：清掉目标账号的密钥与全部恢复码，让它回到单因素登录。它是
 * 「员工换了手机、旧设备也拿不到」这类死局唯一的出口（自救通道`恢复码`也被锁在同一个账号里），
 * 所以它的失败**必须响**——静默失败会让管理员以为已经处理好了，而那位员工依旧进不来。
 *
 * <p>渲染一次的代价不小（ProTable），故两条用例共用同一个 `renderPage`。
 */
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
  resetUserMfa: vi.fn(),
}))
vi.mock('../../services/departmentService', () => ({
  fetchDepartmentTree: vi.fn(async () => []),
  setUserDataPermission: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

const userRow = {
  id: 7,
  username: 'sales01',
  displayName: '销售一',
  role: 'SALES',
  enabled: true,
  version: 0,
}

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  vi.mocked(userService.fetchUsers).mockResolvedValue({
    items: [userRow],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)
  renderWithProviders(<UserManagementPage />)
  await screen.findByText('sales01', {}, { timeout: 5000 })
}

/** Popconfirm 的确认按钮：antd 会在两个汉字之间插空格，故用正则匹配。 */
const confirmButton = () => screen.getByRole('button', { name: /确\s*定/ })

describe('UserManagementPage 重置 2FA（082）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('确认后调用管理员重置端点，目标是该行用户', async () => {
    vi.mocked(userService.resetUserMfa).mockResolvedValue(undefined)
    await renderPage()

    fireEvent.click(screen.getByText('pages.userManagement.reset2fa'))
    // 确认文案要说清后果（密钥与恢复码被清除），否则管理员点的是一个"重置"但不知道拆掉了什么
    expect(screen.getByText(/pages\.userManagement\.reset2faConfirm/)).toBeInTheDocument()

    fireEvent.click(confirmButton())

    await waitFor(() => {
      expect(userService.resetUserMfa).toHaveBeenCalledWith(7)
    })
  })

  it('失败时把后端文案报出来，不静默', async () => {
    vi.mocked(userService.resetUserMfa).mockRejectedValue(
      Object.assign(new Error('无用户管理权限'), {
        isAxiosError: true,
        response: { status: 403, data: { error: { code: 'FORBIDDEN', message: '无用户管理权限' } } },
      }),
    )
    await renderPage()

    fireEvent.click(screen.getByText('pages.userManagement.reset2fa'))
    fireEvent.click(confirmButton())

    await waitFor(() => {
      expect(screen.getByText('无用户管理权限')).toBeInTheDocument()
    })
  })
})
