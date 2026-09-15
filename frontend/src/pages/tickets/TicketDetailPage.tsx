import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Descriptions, Input, List, Modal, Select, Space, Tag, Timeline, Typography } from 'antd'
import { ArrowLeftOutlined, CheckCircleOutlined, SendOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  assignTicket,
  deleteTicket,
  fetchTicket,
  fetchTicketReplies,
  replyTicket,
  transitionTicket,
} from '../../services/ticketService'
import { fetchUsers } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import SurveyBlock from '../../components/SurveyBlock'
import PermissionGuard from '../../components/PermissionGuard'
import {
  TICKET_PRIORITY_COLORS,
  TICKET_SLA_COLORS,
  TICKET_STATUS_COLORS,
  type Ticket,
  type TicketReply,
} from '../../types/ticket'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

export default function TicketDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [ticket, setTicket] = useState<Ticket | null>(null)
  const [replies, setReplies] = useState<TicketReply[]>([])
  const [loading, setLoading] = useState(true)
  const [replyText, setReplyText] = useState('')
  const [assignOpen, setAssignOpen] = useState(false)
  const [assigneeOptions, setAssigneeOptions] = useState<{ value: number; label: string }[]>([])
  const [assigneeId, setAssigneeId] = useState<number | undefined>()
  // 四个动作挂四个**各自独立**的码（TicketController.java:110-135），不共用判据：
  // 删除 ticket:delete、分配 ticket:assign、状态流转 ticket:update、回复 ticket:reply。
  // 「开始处理 / 标记已解决 / 关闭工单」打的是同一个 POST /tickets/{id}/transition、同一个码，
  // 故三处整组同判据，界面上不出现第二个判据。
  const can = usePerms([
    PERMS.ticketDelete,
    PERMS.ticketAssign,
    PERMS.ticketUpdate,
    PERMS.ticketReply,
  ])

  const ticketId = Number(id)

  const load = useCallback(async () => {
    if (!ticketId) return
    setLoading(true)
    try {
      const [t, r] = await Promise.all([
        fetchTicket(ticketId),
        fetchTicketReplies(ticketId, 1, 100),
      ])
      setTicket(t)
      setReplies(r.items)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.ticket.detail.msgLoadFailed')))
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId])

  useEffect(() => {
    void load()
  }, [load])

  const openAssign = async () => {
    const users = await fetchUsers({ page: 1, pageSize: 100 })
    setAssigneeOptions(
      users.items.map((u) => ({ value: u.id, label: u.displayName || u.username })),
    )
    setAssigneeId(ticket?.assigneeId)
    setAssignOpen(true)
  }

  const onAssign = async () => {
    if (!assigneeId) return
    try {
      await assignTicket(ticketId, assigneeId)
      message.success(t('pages.ticket.detail.msgAssigned'))
      setAssignOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.ticket.detail.assignFailed')))
    }
  }

  const onReply = async () => {
    if (!replyText.trim()) {
      message.warning(t('pages.ticket.detail.msgReplyRequired'))
      return
    }
    try {
      await replyTicket(ticketId, replyText.trim())
      setReplyText('')
      message.success(t('pages.ticket.detail.msgReplied'))
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.ticket.detail.replyFailed')))
    }
  }

  const onTransition = async (targetStatus: string) => {
    try {
      await transitionTicket(ticketId, targetStatus)
      message.success(t('pages.ticket.detail.msgStatusUpdated'))
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.ticket.detail.flowFailed')))
    }
  }

  const onDelete = async () => {
    try {
      await deleteTicket(ticketId)
      message.success(t('pages.ticket.detail.msgDeleted'))
      navigate('/tickets')
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.ticket.detail.deleteFailed')))
    }
  }

  if (loading && !ticket) {
    return <Card loading style={{ minHeight: 300 }} />
  }

  if (!ticket) return null

  const canOperate = ticket.status !== 'CLOSED'

  return (
    <>
      <Link to="/tickets" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.ticket.detail.backToList')}
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {ticket.title}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {t('pages.ticket.detail.ticketNo', { id: ticket.id })}
            {ticket.customerName ? ` · ${t('pages.ticket.detail.customerPrefix')}${ticket.customerName}` : ''}
          </Typography.Text>
        </div>
        <Space>
          {ticket.status !== 'CLOSED' && can[PERMS.ticketDelete] && (
            <Button danger onClick={() => void onDelete()}>
              {t('pages.ticket.detail.deleteTicket')}
            </Button>
          )}
        </Space>
      </div>

      <Card
        title={t('pages.ticket.detail.ticketInfo')}
        style={{ marginBottom: 16 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        extra={
          <Space>
            {can[PERMS.ticketAssign] && (
              <Button onClick={() => void openAssign()}>{t('pages.ticket.detail.assignAssignee')}</Button>
            )}
            {can[PERMS.ticketUpdate] && canOperate && ticket.status === 'OPEN' && (
              <Button type="primary" onClick={() => void onTransition('IN_PROGRESS')}>
                {t('pages.ticket.detail.startProcess')}
              </Button>
            )}
            {can[PERMS.ticketUpdate] && canOperate && ticket.status === 'IN_PROGRESS' && (
              <Button type="primary" onClick={() => void onTransition('RESOLVED')}>
                {t('pages.ticket.detail.markResolved')}
              </Button>
            )}
            {can[PERMS.ticketUpdate] && ticket.status === 'RESOLVED' && (
              <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => void onTransition('CLOSED')}>
                {t('pages.ticket.detail.closeTicket')}
              </Button>
            )}
          </Space>
        }
      >
        <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 3 }}>
          <Descriptions.Item label={t('pages.ticket.detail.labelCustomer')}>
            {ticket.customerName ?? `#${ticket.customerId}`}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.ticket.detail.labelStatus')}>
            <Tag color={TICKET_STATUS_COLORS[ticket.status]}>
              {labelOf(t, ENUM_KEYS.ticketStatus, ticket.status)}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.ticket.detail.labelPriority')}>
            <Tag color={TICKET_PRIORITY_COLORS[ticket.priority]}>
              {labelOf(t, ENUM_KEYS.ticketPriority, ticket.priority)}
            </Tag>
          </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelAssignee')}>{ticket.assigneeName ?? t('pages.ticket.detail.unassigned')}</Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelSlaStatus')}>
          {ticket.slaStatus ? (
            <Tag color={TICKET_SLA_COLORS[ticket.slaStatus]}>
              {labelOf(t, ENUM_KEYS.ticketSla, ticket.slaStatus)}
            </Tag>
          ) : (
            '-'
          )}
        </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelCreatedAt')}>
          {ticket.createdAt ? dayjs(ticket.createdAt).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelSlaRespondBy')}>
          {ticket.slaRespondDeadline ? dayjs(ticket.slaRespondDeadline).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelSlaResolveBy')}>
          {ticket.slaResolveDeadline ? dayjs(ticket.slaResolveDeadline).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelReplies')}>{ticket.replyCount ?? replies.length}</Descriptions.Item>
        {ticket.description && (
          <Descriptions.Item label={t('pages.ticket.detail.labelDescription')} span={3}>
            {ticket.description}
          </Descriptions.Item>
        )}
        {ticket.customFieldValues?.map((cf) => (
          <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `${t('pages.customer.detail.fieldPrefix')}${cf.fieldId}`}>
            {cf.value || '-'}
          </Descriptions.Item>
        ))}
        </Descriptions>
      </Card>

      <SurveyBlock
        ticketId={ticket.id}
        isClosed={ticket.status === 'CLOSED'}
        onSubmitted={() => void load()}
      />

      <Card
        title={t('pages.ticket.detail.timeline')}
        style={{ marginBottom: 16 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' }, body: { padding: 0 } }}
      >
        <List
          dataSource={replies}
          locale={{ emptyText: t('pages.ticket.detail.emptyReplies') }}
          renderItem={(reply) => (
            <List.Item style={{ padding: '12px 20px' }}>
              <Timeline
                items={[
                  {
                    color: 'blue',
                    children: (
                      <>
                        <div>
                          <b>{reply.replierName ?? `用户#${reply.replierId}`}</b>
                          <span style={{ color: '#8c8c8c', marginLeft: 12 }}>
                            {reply.createdAt ? dayjs(reply.createdAt).format('YYYY-MM-DD HH:mm') : ''}
                          </span>
                        </div>
                        <div style={{ marginTop: 4 }}>{reply.content}</div>
                      </>
                    ),
                  },
                ]}
              />
            </List.Item>
          )}
        />
      </Card>

      {/* 086 T083：整块条件渲染改用 `PermissionGuard`——本组件此前全库零引用（死代码），
          它的适用场景正是这里："包裹一整块"而不是给单个按钮加判据。
          业务状态判据 `canOperate`（`status !== 'CLOSED'`）留在**外层**、权限判据交给守卫，
          故两者仍是 ∧，与上面几处 `can[...]` 的语义逐字一致，只是换了承载形式。
          守卫内部对 ADMIN 直通（`PermissionGuard.tsx:36`），与 `hasPerm` 同一语义（该处已在护栏白名单）。 */}
      {canOperate && (
        <PermissionGuard permission={PERMS.ticketReply}>
          <div style={{ marginTop: 16 }}>
            <Input.TextArea
              rows={3}
              placeholder={t('pages.ticket.detail.replyPlaceholder')}
              value={replyText}
              onChange={(e) => setReplyText(e.target.value)}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              style={{ marginTop: 8 }}
              onClick={() => void onReply()}
            >
              {t('pages.ticket.detail.sendReply')}
            </Button>
          </div>
        </PermissionGuard>
      )}

      <Modal
        title={t('pages.ticket.detail.assignModal')}
        open={assignOpen}
        onOk={() => void onAssign()}
        onCancel={() => setAssignOpen(false)}
        okText={t('pages.ticket.detail.assignOk')}
        destroyOnClose
      >
        <Select
          showSearch
          optionFilterProp="label"
          style={{ width: '100%' }}
          placeholder={t('pages.ticket.detail.assignPlaceholder')}
          value={assigneeId}
          onChange={setAssigneeId}
          options={assigneeOptions}
        />
      </Modal>
    </>
  )
}
