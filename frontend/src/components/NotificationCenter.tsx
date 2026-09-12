import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { useNotificationSocket } from '../hooks/useNotificationSocket'
import { type Notification, type NotificationType } from '../types/notification'
import { ENUM_KEYS, labelOf } from '../constants/enumLabels'

const TYPE_COLORS: Record<NotificationType, string> = {
  WORKFLOW: 'blue',
  TICKET_ASSIGN: 'orange',
  TICKET_REPLY: 'green',
  // 1.3 的 SLA 升级作业会发这两个类型（`NotificationService.TYPE_SLA_*`）。
  // 不加这两项，工单超时通知的 Tag 颜色是 undefined。
  SLA_WARNING: 'gold',
  SLA_OVERDUE: 'red',
}

export default function NotificationCenter() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState<Notification[]>([])
  const [loading, setLoading] = useState(false)
  const [polling, setPolling] = useState(false)

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

  // 026：WebSocket 实时推送优先；连接失败/不可用时降级 30 秒轮询
  useNotificationSocket(
    (payload) => {
      setUnread(payload.unreadCount)
    },
    () => {
      setPolling(true)
    },
  )

  useEffect(() => {
    if (!polling) return
    void loadCount()
    const timer = window.setInterval(() => void loadCount(), 30000)
    return () => window.clearInterval(timer)
  }, [polling])

  useEffect(() => {
    void loadCount()
    // 首次兜底：无 WebSocket 也同步一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
          aria-label={t('pages.notificationCenter.title')}
        />
      </Badge>
      <Drawer
        title={t('pages.notificationCenter.title')}
        open={open}
        onClose={() => setOpen(false)}
        width={420}
        extra={
          <Button size="small" onClick={() => void onReadAll()} disabled={unread === 0}>
            {t('pages.notificationCenter.btnMarkAllRead')}
          </Button>
        }
      >
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description={t('pages.notificationCenter.empty')} /> }}
          renderItem={(n) => (
            <List.Item
              onClick={() => void onRead(n)}
              style={{ cursor: 'pointer', background: n.read ? undefined : '#e6f4ff' }}
            >
              <List.Item.Meta
                avatar={
                  <Tag color={TYPE_COLORS[n.type as NotificationType]}>
                    {labelOf(t, ENUM_KEYS.notificationType, n.type)}
                  </Tag>
                }
                title={
                  <span>
                    {n.message}
                    {!n.read && <Tag color="red" style={{ marginLeft: 8 }}>{t('pages.notificationCenter.tagUnread')}</Tag>}
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
          <a onClick={() => navigate('/exports')}>{t('pages.notificationCenter.linkExportCenter')}</a>
        </div>
      </Drawer>
    </>
  )
}
