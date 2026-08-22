import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Button, Drawer, Empty, List, Tag } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/notificationService'
import { NOTIFICATION_TYPE_LABELS, type Notification, type NotificationType } from '../types/notification'

const TYPE_COLORS: Record<NotificationType, string> = {
  WORKFLOW: 'blue',
  TICKET_ASSIGN: 'orange',
  TICKET_REPLY: 'green',
}

export default function NotificationCenter() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState<Notification[]>([])
  const [loading, setLoading] = useState(false)

  const loadCount = async () => {
    try {
      setUnread(await fetchUnreadCount())
    } catch {
      // 忽略计数失败
    }
  }

  const loadList = async () => {
    setLoading(true)
    try {
      const res = await fetchNotifications(undefined, 1, 20)
      setItems(res.items)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadCount()
    const timer = window.setInterval(() => void loadCount(), 30000)
    return () => window.clearInterval(timer)
  }, [])

  const onOpen = () => {
    setOpen(true)
    void loadList()
    void loadCount()
  }

  const onRead = async (n: Notification) => {
    if (n.read) return
    await markNotificationRead(n.id)
    setItems((prev) => prev.map((i) => (i.id === n.id ? { ...i, read: true } : i)))
    void loadCount()
  }

  const onReadAll = async () => {
    await markAllNotificationsRead()
    setItems((prev) => prev.map((i) => ({ ...i, read: true })))
    void loadCount()
  }

  return (
    <>
      <Badge count={unread} size="small">
        <Button
          type="text"
          icon={<BellOutlined style={{ fontSize: 17 }} />}
          onClick={onOpen}
          aria-label="通知中心"
        />
      </Badge>
      <Drawer
        title="通知中心"
        open={open}
        onClose={() => setOpen(false)}
        width={420}
        extra={
          <Button size="small" onClick={() => void onReadAll()} disabled={unread === 0}>
            全部已读
          </Button>
        }
      >
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无通知" /> }}
          renderItem={(n) => (
            <List.Item
              onClick={() => void onRead(n)}
              style={{ cursor: 'pointer', background: n.read ? undefined : '#e6f4ff' }}
            >
              <List.Item.Meta
                avatar={
                  <Tag color={TYPE_COLORS[n.type as NotificationType]}>
                    {NOTIFICATION_TYPE_LABELS[n.type as NotificationType]}
                  </Tag>
                }
                title={
                  <span>
                    {n.message}
                    {!n.read && <Tag color="red" style={{ marginLeft: 8 }}>未读</Tag>}
                  </span>
                }
                description={
                  n.createdAt ? dayjs(n.createdAt).format('YYYY-MM-DD HH:mm') : ''
                }
              />
            </List.Item>
          )}
        />
        <div style={{ marginTop: 12, textAlign: 'center' }}>
          <a onClick={() => navigate('/exports')}>前往导出中心</a>
        </div>
      </Drawer>
    </>
  )
}
