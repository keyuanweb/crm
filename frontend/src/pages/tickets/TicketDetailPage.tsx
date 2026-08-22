import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { App, Button, Descriptions, Input, List, Modal, Select, Space, Spin, Tag, Timeline } from 'antd'
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
      message.error(extractErrorMessage(err, '加载失败'))
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
      message.success('已分配')
      setAssignOpen(false)
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '分配失败'))
    }
  }

  const onReply = async () => {
    if (!replyText.trim()) {
      message.warning('请输入回复内容')
      return
    }
    try {
      await replyTicket(ticketId, replyText.trim())
      setReplyText('')
      message.success('已回复')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '回复失败'))
    }
  }

  const onTransition = async (targetStatus: string) => {
    try {
      await transitionTicket(ticketId, targetStatus)
      message.success('状态已更新')
      void load()
    } catch (err) {
      message.error(extractErrorMessage(err, '流转失败'))
    }
  }

  const onDelete = async () => {
    try {
      await deleteTicket(ticketId)
      message.success('已删除')
      navigate('/tickets')
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  if (loading && !ticket) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!ticket) return null

  const canOperate = ticket.status !== 'CLOSED'

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tickets')}>
          返回
        </Button>
        <Button danger onClick={() => void onDelete()}>
          删除工单
        </Button>
      </Space>

      <Descriptions
        title={ticket.title}
        bordered
        size="small"
        column={3}
        extra={
          <Space>
            <Button onClick={() => void openAssign()}>分配处理人</Button>
            {canOperate && ticket.status === 'OPEN' && (
              <Button type="primary" onClick={() => void onTransition('IN_PROGRESS')}>
                开始处理
              </Button>
            )}
            {canOperate && ticket.status === 'IN_PROGRESS' && (
              <Button type="primary" onClick={() => void onTransition('RESOLVED')}>
                标记已解决
              </Button>
            )}
            {ticket.status === 'RESOLVED' && (
              <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => void onTransition('CLOSED')}>
                关闭工单
              </Button>
            )}
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Descriptions.Item label="客户">
          {ticket.customerName ?? `#${ticket.customerId}`}
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={TICKET_STATUS_COLORS[ticket.status]}>{TICKET_STATUS_LABELS[ticket.status]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="优先级">
          <Tag color={TICKET_PRIORITY_COLORS[ticket.priority]}>
            {TICKET_PRIORITY_LABELS[ticket.priority]}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="处理人">{ticket.assigneeName ?? '未分配'}</Descriptions.Item>
        <Descriptions.Item label="SLA 状态">
          {ticket.slaStatus ? (
            <Tag color={TICKET_SLA_COLORS[ticket.slaStatus]}>{TICKET_SLA_LABELS[ticket.slaStatus]}</Tag>
          ) : (
            '无 SLA'
          )}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">
          {ticket.createdAt ? dayjs(ticket.createdAt).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="SLA 响应截止">
          {ticket.slaRespondDeadline ? dayjs(ticket.slaRespondDeadline).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="SLA 解决截止">
          {ticket.slaResolveDeadline ? dayjs(ticket.slaResolveDeadline).format('YYYY-MM-DD HH:mm') : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="回复数">{ticket.replyCount ?? replies.length}</Descriptions.Item>
        {ticket.description && (
          <Descriptions.Item label="问题描述" span={3}>
            {ticket.description}
          </Descriptions.Item>
        )}
        {ticket.customFieldValues?.map((cf) => (
          <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `字段#${cf.fieldId}`}>
            {cf.value || '-'}
          </Descriptions.Item>
        ))}
      </Descriptions>

      <List
        header={<b>处理时间线（{replies.length} 条回复）</b>}
        bordered
        dataSource={replies}
        locale={{ emptyText: '暂无回复' }}
        renderItem={(reply) => (
          <List.Item>
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

      {canOperate && (
        <div style={{ marginTop: 16 }}>
          <Input.TextArea
            rows={3}
            placeholder="输入回复内容..."
            value={replyText}
            onChange={(e) => setReplyText(e.target.value)}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            style={{ marginTop: 8 }}
            onClick={() => void onReply()}
          >
            发送回复
          </Button>
        </div>
      )}

      <Modal
        title="分配处理人"
        open={assignOpen}
        onOk={() => void onAssign()}
        onCancel={() => setAssignOpen(false)}
        okText="分配"
        destroyOnClose
      >
        <Select
          showSearch
          optionFilterProp="label"
          style={{ width: '100%' }}
          placeholder="选择处理人"
          value={assigneeId}
          onChange={setAssigneeId}
          options={assigneeOptions}
        />
      </Modal>
    </>
  )
}
