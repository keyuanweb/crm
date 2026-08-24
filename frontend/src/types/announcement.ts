/** 公告与评论（037）。 */
export interface Announcement {
  id: number
  title: string
  content: string
  pinned: boolean
  expiresAt?: string
  read: boolean
  createdAt?: string
}

export interface AnnouncementPayload {
  title: string
  content: string
  pinned?: boolean
  expiresAt?: string
}

export interface Comment {
  id: number
  entityType: string
  entityId: number
  content: string
  authorId: number
  authorName: string
  createdAt?: string
}
