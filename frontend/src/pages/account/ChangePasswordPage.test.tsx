import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ChangePasswordPage from './ChangePasswordPage'
import { changeOwnPassword } from '../../services/userService'

vi.mock('../../services/userService', () => ({
  changeOwnPassword: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/account/password']}>
      <ChangePasswordPage />
    </MemoryRouter>,
  )
}

describe('ChangePasswordPage（T019）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('两次新密码不一致时提示错误且不提交（FR-006）', async () => {
    renderPage()
    fireEvent.change(screen.getByLabelText(/旧密码/), { target: { value: 'oldPass123' } })
    fireEvent.change(screen.getByLabelText(/^新密码/), { target: { value: 'newPass456' } })
    fireEvent.change(screen.getByLabelText(/确认新密码/), { target: { value: 'diffPass789' } })
    fireEvent.click(screen.getByRole('button', { name: /确认修改/ }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('两次输入的新密码不一致'),
    )
    expect(changeOwnPassword).not.toHaveBeenCalled()
  })

  it('提交调用 changeOwnPassword 并清除登录态（旧令牌失效）', async () => {
    localStorage.setItem('accessToken', 'stale-token')
    localStorage.setItem('refreshToken', 'stale-refresh')
    vi.mocked(changeOwnPassword).mockResolvedValue(undefined)

    renderPage()
    fireEvent.change(screen.getByLabelText(/旧密码/), { target: { value: 'oldPass123' } })
    fireEvent.change(screen.getByLabelText(/^新密码/), { target: { value: 'newPass456' } })
    fireEvent.change(screen.getByLabelText(/确认新密码/), { target: { value: 'newPass456' } })
    fireEvent.click(screen.getByRole('button', { name: /确认修改/ }))

    await waitFor(() =>
      expect(changeOwnPassword).toHaveBeenCalledWith('oldPass123', 'newPass456'),
    )
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })
})
