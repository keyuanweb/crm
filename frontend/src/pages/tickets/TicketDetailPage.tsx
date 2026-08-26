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
import SurveyBlock from '../../components/SurveyBlock'
import {
  TICKET_PRIORITY_COLORS,
  TICKET_PRIORITY_LABELS,
  TICKET_SLA_COLORS,
  TICKET_SLA_LABELS,
  TICKET_STATUS_COLORS,
  TICKET_STATUS_LABELS,
  type Ticket,
  type TicketReply,
} from '../../types/ticket'

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
          {ticket.status !== 'CLOSED' && (
            <Button danger onClick={() => void onDelete()}>
              {t('pages.ticket.detail.deleteTicket')}
            </Button>
          )}
        </Space>
      </div>

      <Card
        title={t('pages.ticket.detail.ticketInfo')}
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        extra={
          <Space>
            <Button onClick={() => void openAssign()}>{t('pages.ticket.detail.assignAssignee')}</Button>
            {canOperate && ticket.status === 'OPEN' && (
              <Button type="primary" onClick={() => void onTransition('IN_PROGRESS')}>
                {t('pages.ticket.detail.startProcess')}
              </Button>
            )}
            {canOperate && ticket.status === 'IN_PROGRESS' && (
              <Button type="primary" onClick={() => void onTransition('RESOLVED')}>
                {t('pages.ticket.detail.markResolved')}
              </Button>
            )}
            {ticket.status === 'RESOLVED' && (
              <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => void onTransition('CLOSED')}>
                {t('pages.ticket.detail.closeTicket')}
              </Button>
            )}
          </Space>
        }
      >
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label={t('pages.ticket.detail.labelCustomer')}>
            {ticket.customerName ?? `#${ticket.customerId}`}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.ticket.detail.labelStatus')}>
            <Tag color={TICKET_STATUS_COLORS[ticket.status]}>{TICKET_STATUS_LABELS[ticket.status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.ticket.detail.labelPriority')}>
            <Tag color={TICKET_PRIORITY_COLORS[ticket.priority]}>
              {TICKET_PRIORITY_LABELS[ticket.priority]}
            </Tag>
          </Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelAssignee')}>{ticket.assigneeName ?? t('pages.ticket.detail.unassigned')}</Descriptions.Item>
        <Descriptions.Item label={t('pages.ticket.detail.labelSlaStatus')}>
          {ticket.slaStatus ? (
            <Tag color={TICKET_SLA_COLORS[ticket.slaStatus]}>{TICKET_SLA_LABELS[ticket.slaStatus]}</Tag>
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
          <Descriptions.Item label={t('pages.ticket.detail.labelDescription')} span={2}>
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
        style={{ marginBottom: 16, borderRadius: 10 }}
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

      {canOperate && (
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
