import { describe, expect, it, vi, beforeEach } from 'vitest'
import { waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { fetchLeads } from '../../services/leadService'
import LeadListPage from './LeadListPage'

// 渲染冒烟：若页面存在渲染期异常（崩溃/白屏），测试将抛出真实错误
//
// 084 顺带修的一处旧缺陷：这两条路径原先写成 `'../services/xxx'`，而本文件在 `src/pages/leads/`，
// `'../services/'` 指向的是不存在的 `src/pages/services/`。mock 因此静默失效，测试照样绿——
// 它跑的是「真实请求失败后的错误态」，而不是它声称的场景。改对之后下面那条
// 「列表接口真的被调用」的断言才有意义（顺带证明 mock 没有被再次改坏）。
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

describe('LeadListPage 渲染冒烟（防止白屏/错误页回归）', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('首次渲染不抛异常，且列表接口真的走的是 mock（mock 失效会让本用例变红）', async () => {
    expect(() => renderWithProviders(<LeadListPage />, { route: '/leads' })).not.toThrow()
    // 「不抛异常」单独存在时会假绿：请求失败被吞进错误态同样不抛。接口被调用才说明
    // 页面真的走完了「取数 → 渲染」这条路，且 mock 路径是对的。
    await waitFor(() => expect(fetchLeads).toHaveBeenCalled(), { timeout: 20000 })
  })
})
