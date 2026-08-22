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

export const ARTICLE_CATEGORY_LABELS: Record<ArticleCategory, string> = {
  PRODUCT_USAGE: '产品使用',
  FAULT_TROUBLESHOOTING: '故障排查',
  PROCESS_CONSULT: '流程咨询',
  AFTER_SALES_POLICY: '售后政策',
  OTHER: '其他',
}

export const ARTICLE_STATUS_LABELS: Record<ArticleStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
}
