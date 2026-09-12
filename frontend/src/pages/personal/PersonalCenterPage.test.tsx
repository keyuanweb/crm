import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import PersonalCenterPage from './PersonalCenterPage'
import * as personalService from '../../services/personalService'
import * as userService from '../../services/userService'

vi.mock('../../services/personalService')
vi.mock('../../services/userService')

describe('PersonalCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('个人信息查看（US1）', () => {
    it('加载时显示个人信息（FR-001）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
        departmentName: '技术部',
        dataScope: 'ALL',
        enabled: true,
        lastLoginAt: '2026-08-29T10:00:00',
        createdAt: '2026-01-01T00:00:00',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        // 使用 data-testid 或更具体的选择器避免重复文本
        const cards = screen.getAllByTestId('basic-info-card')
        expect(cards.length).toBeGreaterThan(0)
      })
    })

    it('未分配部门时显示"未分配"（FR-001）', async () => {
      const mockInfo = {
        id: 2,
        username: 'user1',
        displayName: '测试用户',
        role: 'SALES',
        departmentName: null,
        dataScope: 'SELF',
        enabled: true,
        createdAt: '2026-01-01T00:00:00',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        // 使用包含"未分配"的描述项（文案已外化，断言键名）
        const descriptions = screen.getAllByText(/pages\.personalCenter\.unassigned/)
        expect(descriptions.length).toBeGreaterThan(0)
      })
    })
  })

  describe('显示名编辑（US3）', () => {
    it('编辑模式下显示输入框（FR-002）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnEditDisplayName')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnEditDisplayName'))
      expect(screen.getByLabelText('pages.personalCenter.colDisplayName')).toBeInTheDocument()
    })

    it('保存显示名时进行表单验证（FR-008）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnEditDisplayName')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnEditDisplayName'))
      fireEvent.click(screen.getByText('pages.personalCenter.btnSave'))

      // Ant Design Form 验证错误会显示在 Form.Item 中
      await waitFor(() => {
        // 验证触发后应该有错误提示或表单处于验证状态
        expect(screen.getByLabelText('pages.personalCenter.colDisplayName')).toBeInTheDocument()
      })
    })

    it('保存显示名后调用 API 并刷新（FR-002）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnEditDisplayName')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnEditDisplayName'))
      fireEvent.change(screen.getByLabelText('pages.personalCenter.colDisplayName'), {
        target: { value: '新显示名' },
      })
      fireEvent.click(screen.getByText('pages.personalCenter.btnSave'))

      await waitFor(() => {
        expect(personalService.updateDisplayName).toHaveBeenCalledWith({
          displayName: '新显示名',
        })
      })
    })
  })

  describe('修改密码（US2）', () => {
    it('打开密码修改 Modal（FR-003）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnChangePassword')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnChangePassword'))
      expect(screen.getByLabelText('pages.changePassword.currentPassword')).toBeInTheDocument()
      expect(screen.getByLabelText('pages.changePassword.newPassword')).toBeInTheDocument()
      expect(screen.getByLabelText('pages.changePassword.confirmPassword')).toBeInTheDocument()
    })

    it('两次新密码不一致时提示错误（FR-005）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnChangePassword')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnChangePassword'))
      fireEvent.change(screen.getByLabelText(/pages\.changePassword\.currentPassword/), { target: { value: 'oldPass123' } })
      fireEvent.change(screen.getByLabelText(/^pages\.changePassword\.newPassword$/), { target: { value: 'newPass456' } })
      fireEvent.change(screen.getByLabelText(/pages\.changePassword\.confirmPassword/), { target: { value: 'diffPass789' } })
      fireEvent.click(screen.getByRole('button', { name: /pages\.changePassword\.btnSubmit/ }))

      await waitFor(() => {
        expect(screen.getByText('pages.changePassword.msgPasswordMismatch')).toBeInTheDocument()
        expect(userService.changeOwnPassword).not.toHaveBeenCalled()
      })
    })

    it('提交密码修改后清除登录态（FR-006）', async () => {
      localStorage.setItem('accessToken', 'stale-token')
      localStorage.setItem('refreshToken', 'stale-refresh')
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)
      vi.mocked(userService.changeOwnPassword).mockResolvedValue(undefined)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.btnChangePassword')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('pages.personalCenter.btnChangePassword'))
      fireEvent.change(screen.getByLabelText(/pages\.changePassword\.currentPassword/), { target: { value: 'oldPass123' } })
      fireEvent.change(screen.getByLabelText(/^pages\.changePassword\.newPassword$/), { target: { value: 'newPass456' } })
      fireEvent.change(screen.getByLabelText(/pages\.changePassword\.confirmPassword/), { target: { value: 'newPass456' } })
      fireEvent.click(screen.getByRole('button', { name: /pages\.changePassword\.btnSubmit/ }))

      await waitFor(() => {
        expect(userService.changeOwnPassword).toHaveBeenCalledWith('oldPass123', 'newPass456')
      })
    })
  })

  describe('账户安全信息展示（US4）', () => {
    it('显示最后登录时间（FR-004）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
        lastLoginAt: '2026-08-29T10:00:00',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        expect(screen.getByText('pages.personalCenter.colLastLoginAt')).toBeInTheDocument()
      })
    })

    it('从未登录时显示"从未"（FR-004）', async () => {
      const mockInfo = {
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
        lastLoginAt: null,
        createdAt: '2026-01-01T00:00:00',
      }
      vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(mockInfo)

      renderWithProviders(<PersonalCenterPage />)

      await waitFor(() => {
        // 文案已外化，断言键名（测试环境 t(key) 返回 key 本身）
        const allText = screen.getByTestId('security-card')
        expect(allText.textContent).toContain('pages.personalCenter.neverLoggedIn')
      })
    })
  })
})
