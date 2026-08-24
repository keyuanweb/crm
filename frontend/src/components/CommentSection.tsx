import { useCallback, useEffect, useState } from 'react'
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
      message.warning('请输入评论内容')
      return
    }
    setSubmitting(true)
    try {
      await createComment(entityType, entityId, content.trim())
      setContent('')
      message.success('已发表')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '发表失败'))
    } finally {
      setSubmitting(false)
    }
  }

  const onDelete = async (c: Comment) => {
    try {
      await deleteComment(c.id)
      message.success('已删除')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  const canDelete = (c: Comment) => user?.role === 'ADMIN' || user?.id === c.authorId

  return (
    <div>
      <Space align="center" style={{ marginBottom: 12 }}>
        <CommentOutlined style={{ color: '#1677ff' }} />
        <Typography.Text strong>评论协作</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          输入 @用户名 可提醒同事
        </Typography.Text>
      </Space>

      <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
        <Input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="发表评论，@用户名 提醒同事…"
          onPressEnter={() => void onSubmit()}
        />
        <Button type="primary" loading={submitting} onClick={() => void onSubmit()}>
          发表
        </Button>
      </Space.Compact>

      {comments.length === 0 && !loading ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评论" style={{ padding: 12 }} />
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
                    <Popconfirm title="删除该评论？" onConfirm={() => void onDelete(c)}>
                      <a style={{ fontSize: 12, color: '#ff4d4f' }}>
                        <DeleteOutlined /> 删除
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
