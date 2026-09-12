export type ArticleCategory =
  | 'PRODUCT_USAGE'
  | 'FAULT_TROUBLESHOOTING'
  | 'PROCESS_CONSULT'
  | 'AFTER_SALES_POLICY'
  | 'OTHER'
export type ArticleStatus = 'DRAFT' | 'PUBLISHED'

export interface KnowledgeArticle {
  id: number
  category: ArticleCategory
  title: string
  content?: string
  keywords?: string
  status: ArticleStatus
  authorId?: number
  authorName?: string
  version: number
  createdAt?: string
}

export interface ArticlePayload {
  category: string
  title: string
  content?: string
  keywords?: string
  version?: number
}

export interface ArticleListParams {
  keyword?: string
  category?: string
  status?: string
  includeDraft?: boolean
  page: number
  pageSize: number
}
