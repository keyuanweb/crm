export interface CurrencyRate {
  id: number
  code: string
  name: string
  rate: number
  isBase: boolean
  enabled: boolean
  version: number
  updatedAt?: string
}

export interface CurrencyRatePayload {
  code: string
  name: string
  rate: number
  enabled?: boolean
  version?: number
}

export interface ProductPriceView {
  productId: number
  basePrice: number
  prices: {
    currencyCode: string
    price: number
    converted: number
    configured: boolean
  }[]
}
