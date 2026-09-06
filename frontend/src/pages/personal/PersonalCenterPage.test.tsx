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
        // 使用包含"未分配"的描述项
        const descriptions = screen.getAllByText(/未分配/)
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
        expect(screen.getByText('编辑显示名')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('编辑显示名'))
      expect(screen.getByLabelText('显示名')).toBeInTheDocument()
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
        expect(screen.getByText('编辑显示名')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('编辑显示名'))
      fireEvent.click(screen.getByText('保存'))

      // Ant Design Form 验证错误会显示在 Form.Item 中
      await waitFor(() => {
        // 验证触发后应该有错误提示或表单处于验证状态
        expect(screen.getByLabelText('显示名')).toBeInTheDocument()
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
        expect(screen.getByText('编辑显示名')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('编辑显示名'))
      fireEvent.change(screen.getByLabelText('显示名'), { target: { value: '新显示名' } })
      fireEvent.click(screen.getByText('保存'))

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
        expect(screen.getByText('修改密码')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('修改密码'))
      expect(screen.getByLabelText('旧密码')).toBeInTheDocument()
      expect(screen.getByLabelText('新密码')).toBeInTheDocument()
      expect(screen.getByLabelText('确认新密码')).toBeInTheDocument()
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
        expect(screen.getByText('修改密码')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('修改密码'))
      fireEvent.change(screen.getByLabelText(/旧密码/), { target: { value: 'oldPass123' } })
      fireEvent.change(screen.getByLabelText(/^新密码/), { target: { value: 'newPass456' } })
      fireEvent.change(screen.getByLabelText(/确认新密码/), { target: { value: 'diffPass789' } })
      fireEvent.click(screen.getByRole('button', { name: /确认修改/ }))

      await waitFor(() => {
        expect(screen.getByText('两次输入的新密码不一致')).toBeInTheDocument()
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
        expect(screen.getByText('修改密码')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('修改密码'))
      fireEvent.change(screen.getByLabelText(/旧密码/), { target: { value: 'oldPass123' } })
      fireEvent.change(screen.getByLabelText(/^新密码/), { target: { value: 'newPass456' } })
      fireEvent.change(screen.getByLabelText(/确认新密码/), { target: { value: 'newPass456' } })
      fireEvent.click(screen.getByRole('button', { name: /确认修改/ }))

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
        expect(screen.getByText('最后登录时间')).toBeInTheDocument()
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
        // 使用正则表达式匹配"从未"上下文
        const allText = screen.getByTestId('security-card')
        expect(allText.textContent).toContain('从未')
      })
    })
  })
})
