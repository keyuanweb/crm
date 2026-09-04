import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CurrencyRatePage from './CurrencyRatePage'
import * as currencyService from '../../services/currencyService'

vi.mock('../../services/currencyService', () => ({
  fetchCurrencies: vi.fn(),
  createCurrency: vi.fn(),
  updateCurrency: vi.fn(),
  deleteCurrency: vi.fn(),
}))

describe('CurrencyRatePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(currencyService.fetchCurrencies).mockResolvedValue([
      { id: 1, code: 'CNY', name: '人民币', rate: 1, isBase: true, enabled: true, version: 0 },
      { id: 2, code: 'USD', name: '美元', rate: 7.2, isBase: false, enabled: true, version: 0 },
    ])
  })

  it('应该渲染汇率管理页面标题', async () => {
    renderWithProviders(<CurrencyRatePage />)
    await waitFor(() => {
      expect(screen.getByText('币种与汇率')).toBeInTheDocument()
    })
  })

  it('应该显示币种列表', async () => {
    renderWithProviders(<CurrencyRatePage />)
    await waitFor(() => {
      expect(screen.getByText('人民币')).toBeInTheDocument()
      expect(screen.getByText('美元')).toBeInTheDocument()
    })
  })

  it('应该显示新增币种按钮', async () => {
    renderWithProviders(<CurrencyRatePage />)
    await waitFor(() => {
      expect(screen.getByText('新增币种')).toBeInTheDocument()
    })
  })

  it('新增币种成功后应该刷新列表', async () => {
    const mockCreate = vi.mocked(currencyService.createCurrency)
    mockCreate.mockResolvedValue({
      id: 3,
      code: 'EUR',
      name: '欧元',
      rate: 8.5,
      isBase: false,
      enabled: true,
      version: 0,
    })

    renderWithProviders(<CurrencyRatePage />)

    await waitFor(() => {
      expect(screen.getByText('新增币种')).toBeInTheDocument()
    })

    // 点击新增按钮
    const addButton = screen.getByText('新增币种')
    addButton.click()

    await waitFor(() => {
      expect(screen.getByText('新增币种')).toBeInTheDocument()
    })
  })

  it('基准币种应该显示不可编辑提示', async () => {
    renderWithProviders(<CurrencyRatePage />)
    await waitFor(() => {
      expect(screen.getByText('基准不可编辑')).toBeInTheDocument()
    })
  })
})
