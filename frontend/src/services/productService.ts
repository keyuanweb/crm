import { apiClient, type PageResult } from './apiClient'
import type { Product, ProductListParams, ProductPayload } from '../types/product'

export type { ProductListParams, ProductPayload }

export async function fetchProducts(params: ProductListParams): Promise<PageResult<Product>> {
  const { data } = await apiClient.get('/products', { params })
  return data.data as PageResult<Product>
}

export async function createProduct(payload: ProductPayload): Promise<Product> {
  const { data } = await apiClient.post('/products', payload)
  return data.data as Product
}

export async function updateProduct(id: number, payload: ProductPayload): Promise<Product> {
  const { data } = await apiClient.put(`/products/${id}`, payload)
  return data.data as Product
}

export async function deleteProduct(id: number): Promise<void> {
  await apiClient.delete(`/products/${id}`)
}
