import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderWithProviders } from './test/renderWithProviders'
import App from './App'

// 全量 App 渲染冒烟：覆盖 RequireAuth → Shell（Header/菜单）→ 页面路由
vi.mock('../../services/authService', () => ({
  fetchMe: vi.fn(async () => ({ id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' })),
  logout: vi.fn(),
  login: vi.fn(),
}))
vi.mock('../../services/leadService', () => ({
  fetchLeads: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createLead: vi.fn(),
  updateLead: vi.fn(),
  deleteLead: vi.fn(),
  claimLead: vi.fn(),
  assignLead: vi.fn(),
}))
vi.mock('../../services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(async () => []),
  fetchCustomFields: vi.fn(),
  createCustomField: vi.fn(),
  updateCustomField: vi.fn(),
  deleteCustomField: vi.fn(),
}))
// 顶栏通知中心会发起真实请求，测试中 mock 避免 jsdom XHR 报错
vi.mock('./services/notificationService', () => ({
  fetchUnreadCount: vi.fn(async () => 0),
  fetchNotifications: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
}))

describe('App 全量渲染冒烟（/leads 白屏/错误页回归）', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('accessToken', 'test-token')
  })

  it('访问 /leads 时 App（Shell+菜单+页面）同步渲染不抛异常', async () => {
    expect(() =>
      renderWithProviders(<App />, { route: '/leads' }),
    ).not.toThrow()
  })
})
