import { apiClient, type PageResult } from './apiClient'
import type {
  ArticleListParams,
  ArticlePayload,
  KnowledgeArticle,
} from '../types/knowledge'

export type { ArticleListParams, ArticlePayload }

export async function fetchArticles(params: ArticleListParams): Promise<PageResult<KnowledgeArticle>> {
  const { data } = await apiClient.get('/knowledge', { params })
  return data.data as PageResult<KnowledgeArticle>
}

export async function createArticle(payload: ArticlePayload): Promise<KnowledgeArticle> {
  const { data } = await apiClient.post('/knowledge', payload)
  return data.data as KnowledgeArticle
}

export async function updateArticle(id: number, payload: ArticlePayload): Promise<KnowledgeArticle> {
  const { data } = await apiClient.put(`/knowledge/${id}`, payload)
  return data.data as KnowledgeArticle
}

export async function publishArticle(id: number): Promise<KnowledgeArticle> {
  const { data } = await apiClient.post(`/knowledge/${id}/publish`)
  return data.data as KnowledgeArticle
}

export async function unpublishArticle(id: number): Promise<KnowledgeArticle> {
  const { data } = await apiClient.post(`/knowledge/${id}/unpublish`)
  return data.data as KnowledgeArticle
}

export async function deleteArticle(id: number): Promise<void> {
  await apiClient.delete(`/knowledge/${id}`)
}
