import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App, Badge, Button, Card, Empty, List, Space, Tag, Typography } from 'antd'
import { BellOutlined, PushpinOutlined } from '@ant-design/icons'
import { fetchAnnouncements, markAnnouncementRead } from '../services/announcementService'
import { extractErrorMessage } from '../services/apiClient'
import type { Announcement } from '../types/announcement'

/** 首页公告卡（037）：最新公告 + 未读角标 + 已读。 */
export default function AnnouncementCard() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const res = await fetchAnnouncements({ page: 1, pageSize: 5 })
      setItems(res.items)
    } catch {
      // 忽略
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const onRead = async (a: Announcement) => {
    try {
      await markAnnouncementRead(a.id)
      message.success('已读')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const unread = items.filter((a) => !a.read).length

  return (
    <Card
      title={
        <Space>
          <Badge count={unread} size="small" offset={[6, 0]}>
            <BellOutlined style={{ fontSize: 15 }} />
          </Badge>
          <span>团队公告</span>
        </Space>
      }
      extra={
        <Button type="link" size="small" onClick={() => navigate('/announcements')}>
          查看全部
        </Button>
      }
      style={{ borderRadius: 12, height: '100%' }}
      styles={{ body: { paddingTop: 8 } }}
    >
      <List
        loading={loading}
        dataSource={items}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公告" /> }}
        renderItem={(a) => (
          <List.Item
            style={{ padding: '8px 0' }}
            actions={[
              !a.read ? (
                <Button key="read" size="small" type="link" onClick={() => void onRead(a)}>
                  标记已读
                </Button>
              ) : null,
            ]}
          >
            <List.Item.Meta
              title={
                <Space size={6}>
                  {a.pinned ? (
                    <Tag icon={<PushpinOutlined />} color="orange" style={{ marginRight: 0 }}>
                      置顶
                    </Tag>
                  ) : null}
                  <Typography.Text
                    strong={!a.read}
                    style={a.read ? { color: '#8c8c8c' } : undefined}
                    ellipsis
                  >
                    {a.title}
                  </Typography.Text>
                </Space>
              }
              description={
                <Typography.Paragraph
                  type="secondary"
                  ellipsis={{ rows: 1 }}
                  style={{ marginBottom: 0, fontSize: 12 }}
                >
                  {a.content.replace(/<[^>]*>/g, '')}
                </Typography.Paragraph>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  )
}
