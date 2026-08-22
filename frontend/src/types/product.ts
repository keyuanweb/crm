export interface Product {
  id: number
  code: string
  name: string
  spec?: string
  unit?: string
  standardPrice: number
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  createdAt?: string
}

export interface ProductPayload {
  code: string
  name: string
  spec?: string
  unit?: string
  standardPrice: number
  status?: string
  version?: number
}

export interface ProductListParams {
  keyword?: string
  status?: string
  page: number
  pageSize: number
}
