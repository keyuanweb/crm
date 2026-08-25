import { apiClient } from './apiClient'
import type { CurrencyRate, CurrencyRatePayload, ProductPriceView } from '../types/currency'

export async function fetchCurrencies(): Promise<CurrencyRate[]> {
  const { data } = await apiClient.get('/currencies')
  return data.data as CurrencyRate[]
}

export async function createCurrency(payload: CurrencyRatePayload): Promise<CurrencyRate> {
  const { data } = await apiClient.post('/currencies', payload)
  return data.data as CurrencyRate
}

export async function updateCurrency(id: number, payload: CurrencyRatePayload): Promise<CurrencyRate> {
  const { data } = await apiClient.put(`/currencies/${id}`, payload)
  return data.data as CurrencyRate
}

export async function deleteCurrency(id: number): Promise<void> {
  await apiClient.delete(`/currencies/${id}`)
}

export async function convertAmount(
  amount: number,
  fromCurrency: string,
  toCurrency: string,
): Promise<{ convertedAmount: number }> {
  const { data } = await apiClient.post('/currencies/convert', { amount, fromCurrency, toCurrency })
  return data.data as { convertedAmount: number }
}

export async function fetchProductPrices(productId: number): Promise<ProductPriceView> {
  const { data } = await apiClient.get(`/products/${productId}/prices`)
  return data.data as ProductPriceView
}

export async function setProductPrice(
  productId: number,
  currencyCode: string,
  price: number,
): Promise<void> {
  await apiClient.post(`/products/${productId}/prices`, { currencyCode, price })
}

export async function deleteProductPrice(productId: number, currencyCode: string): Promise<void> {
  await apiClient.delete(`/products/${productId}/prices/${currencyCode}`)
}
