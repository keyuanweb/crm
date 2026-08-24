import { apiClient, type PageResult } from './apiClient'
import type { Comment } from '../types/announcement'

export async function fetchComments(
  entityType: string,
  entityId: number,
  params?: { page?: number; pageSize?: number },
): Promise<PageResult<Comment>> {
  const { data } = await apiClient.get('/comments', { params: { entityType, entityId, ...params } })
  return data.data as PageResult<Comment>
}

export async function createComment(
  entityType: string,
  entityId: number,
  content: string,
): Promise<Comment> {
  const { data } = await apiClient.post('/comments', { entityType, entityId, content })
  return data.data as Comment
}

export async function deleteComment(id: number): Promise<void> {
  await apiClient.delete(`/comments/${id}`)
}
