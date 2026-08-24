import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import LeadListPage from './LeadListPage'

// 渲染冒烟：若页面存在渲染期异常（崩溃/白屏），测试将抛出真实错误
vi.mock('../services/leadService', () => ({
  fetchLeads: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createLead: vi.fn(),
  updateLead: vi.fn(),
  deleteLead: vi.fn(),
  claimLead: vi.fn(),
  assignLead: vi.fn(),
}))
vi.mock('../services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(async () => []),
  fetchCustomFields: vi.fn(),
  createCustomField: vi.fn(),
  updateCustomField: vi.fn(),
  deleteCustomField: vi.fn(),
}))

describe('LeadListPage 渲染冒烟（防止白屏/错误页回归）', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('首次渲染不抛异常', async () => {
    expect(() => renderWithProviders(<LeadListPage />, { route: '/leads' })).not.toThrow()
  })
})
