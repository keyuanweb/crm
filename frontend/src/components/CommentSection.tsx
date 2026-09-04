import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Avatar, Button, Empty, Input, Popconfirm, Space, Timeline, Typography } from 'antd'
import { CommentOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons'
import { createComment, deleteComment, fetchComments } from '../services/commentService'
import { extractErrorMessage } from '../services/apiClient'
import { useAuthStore } from '../store/authStore'
import type { Comment } from '../types/announcement'

/** 通用评论区（037）：客户/线索/商机/工单详情复用，支持 @提及。 */
export default function CommentSection({
  entityType,
  entityId,
}: {
  entityType: string
  entityId: number
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const [comments, setComments] = useState<Comment[]>([])
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    if (!entityId) return
    setLoading(true)
    try {
      const res = await fetchComments(entityType, entityId, { page: 1, pageSize: 50 })
      setComments(res.items)
    } catch {
      // 忽略
    } finally {
      setLoading(false)
    }
  }, [entityType, entityId])

  useEffect(() => {
    void load()
  }, [load])

  const onSubmit = async () => {
    if (!content.trim()) {
      message.warning(t('pages.commentSection.msgInputRequired'))
      return
    }
    setSubmitting(true)
    try {
      await createComment(entityType, entityId, content.trim())
      setContent('')
      message.success(t('pages.commentSection.msgPublished'))
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.commentSection.msgPublishFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  const onDelete = async (c: Comment) => {
    try {
      await deleteComment(c.id)
      message.success(t('pages.commentSection.msgDeleted'))
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.commentSection.msgDeleteFailed')))
    }
  }

  const canDelete = (c: Comment) => user?.role === 'ADMIN' || user?.id === c.authorId

  return (
    <div>
      <Space align="center" style={{ marginBottom: 12 }}>
        <CommentOutlined style={{ color: '#1677ff' }} />
        <Typography.Text strong>{t('pages.commentSection.title')}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {t('pages.commentSection.tipMention')}
        </Typography.Text>
      </Space>

      <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
        <Input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder={t('pages.commentSection.placeholder')}
          onPressEnter={() => void onSubmit()}
        />
        <Button type="primary" loading={submitting} onClick={() => void onSubmit()}>
          {t('pages.commentSection.btnSubmit')}
        </Button>
      </Space.Compact>

      {comments.length === 0 && !loading ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages.commentSection.empty')} style={{ padding: 12 }} />
      ) : (
        <Timeline
          items={comments.map((c) => ({
            children: (
              <div>
                <Space size={8} style={{ marginBottom: 2 }}>
                  <Avatar size="small" icon={<UserOutlined />} style={{ background: '#1677ff' }} />
                  <Typography.Text strong style={{ fontSize: 13 }}>
                    {c.authorName}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {c.createdAt ? c.createdAt.replace('T', ' ').slice(0, 16) : ''}
                  </Typography.Text>
                  {canDelete(c) && (
                    <Popconfirm title={t('pages.commentSection.confirmDelete')} onConfirm={() => void onDelete(c)}>
                      <a style={{ fontSize: 12, color: '#ff4d4f' }}>
                        <DeleteOutlined /> {t('pages.commentSection.btnDelete')}
                      </a>
                    </Popconfirm>
                  )}
                </Space>
                <div style={{ fontSize: 13, paddingLeft: 32 }}>{c.content}</div>
              </div>
            ),
          }))}
        />
      )}
    </div>
  )
}
