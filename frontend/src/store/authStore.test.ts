import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ user: null })
  })

  it('setTokens 写入令牌且 isAuthenticated 反映状态', () => {
    useAuthStore.getState().setTokens('access-1', 'refresh-1')
    expect(localStorage.getItem('accessToken')).toBe('access-1')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-1')
    expect(useAuthStore.getState().isAuthenticated()).toBe(true)
    expect(useAuthStore.getState().getAccessToken()).toBe('access-1')
  })

  it('clear 清除令牌与用户信息', () => {
    useAuthStore.getState().setTokens('access-1', 'refresh-1')
    useAuthStore
      .getState()
      .setUser({ id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' })
    useAuthStore.getState().clear()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it('setUser 更新当前用户', () => {
    useAuthStore.getState().setUser({ id: 2, username: 'sales', displayName: '销售', role: 'SALES' })
    expect(useAuthStore.getState().user?.role).toBe('SALES')
  })
})
