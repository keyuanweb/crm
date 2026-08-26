import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Progress,
  Result,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, EditOutlined, MailOutlined, PhoneOutlined, ShareAltOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { useCustomerDetail } from '../../hooks/useCustomers'
import { shareCustomer } from '../../services/customerShareService'
import { fetchUsers } from '../../services/userService'
import { updateCustomer, type CustomerPayload } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { formatAmount } from '../../types/opportunity'
import { useAuthStore } from '../../store/authStore'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import ContactsCard from '../../components/ContactsCard'
import CommentSection from '../../components/CommentSection'
import type {
  ContractBrief,
  OpportunityBrief,
  OrderBrief,
  PaymentSummary,
  TicketBrief,
} from '../../types/customer'

export default function CustomerDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const customerId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const [shareOpen, setShareOpen] = useState(false)
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [shareForm] = Form.useForm<{ sharedToUserId: number }>()
  // 编辑客户
  const [editOpen, setEditOpen] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editForm] = Form.useForm<CustomerPayload>()
  // 031：客户标签
  const [customerTags, setCustomerTags] = useState<{ id: number; name: string; color?: string }[]>([])
  const [tagOpen, setTagOpen] = useState(false)
  const [tagOptions, setTagOptions] = useState<{ label: string; value: number }[]>([])
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([])
  const { data, isLoading, error } = useCustomerDetail(
    Number.isFinite(customerId) ? customerId : undefined,
  )

  const openShare = async () => {
    const users = await fetchUsers({ page: 1, pageSize: 100 })
    setUserOptions(
      users.items
        .filter((u) => u.role !== 'SUPPORT' && u.id !== user?.id)
        .map((u) => ({ value: u.id, label: u.displayName || u.username })),
    )
    shareForm.resetFields()
    setShareOpen(true)
  }

  // 优化：详情页编辑客户（核心字段）
  const openEdit = () => {
    if (!data) return
    editForm.setFieldsValue({
      name: data.name,
      company: data.company,
      contactPerson: data.contactPerson,
      phone: data.phone,
      email: data.email,
      address: data.address,
      status: data.status,
      remark: data.remark,
    })
    setEditOpen(true)
  }

  const onSaveEdit = async () => {
    const values = await editForm.validateFields()
    const payload: CustomerPayload = { name: values.name, company: values.company }
    for (const [key, value] of Object.entries(values)) {
      if (key !== 'name' && key !== 'company' && value !== undefined && value !== '') {
        payload[key as keyof CustomerPayload] = value as never
      }
    }
    setEditSaving(true)
    try {
      await updateCustomer(customerId, { ...payload, version: data?.version })
      message.success(t('pages.customer.detail.msgSaved'))
      setEditOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['customer', customerId] })
      void queryClient.invalidateQueries({ queryKey: ['customers'] })
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setEditSaving(false)
    }
  }

  // 031：加载客户标签
  const loadTags = async () => {
    if (!customerId) return
    try {
      const { fetchCustomerTags } = await import('../../services/tagService')
      const tags = await fetchCustomerTags(customerId)
      setCustomerTags(tags)
    } catch {
      // 忽略
    }
  }

  useEffect(() => {
    void loadTags()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId])

  const onEditTags = async () => {
    if (!customerId) return
    const { fetchTags } = await import('../../services/tagService')
    const all = await fetchTags('CUSTOMER')
    setTagOptions(all.map((t) => ({ label: t.name, value: t.id })))
    setSelectedTagIds(customerTags.map((t) => t.id))
    setTagOpen(true)
  }

  const onSaveTags = async () => {
    if (!customerId) return
    const { setCustomerTags } = await import('../../services/tagService')
    await setCustomerTags(customerId, selectedTagIds)
    message.success(t('pages.customer.detail.msgTagsUpdated'))
    setTagOpen(false)
    void loadTags()
  }

  const onShare = async () => {
    const values = await shareForm.validateFields()
    try {
      await shareCustomer(customerId, values.sharedToUserId)
      message.success(t('pages.customer.detail.msgShared'))
      setShareOpen(false)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.customer.detail.shareFailed')))
    }
  }

  const canShare = data && (isAdmin || data.ownerId === user?.id)

  if (isLoading) {
    return (
      <Card loading style={{ minHeight: 300 }} />
    )
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title={t('pages.customer.detail.notFound')}
        extra={
          <Link to="/customers">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              {t('pages.customer.detail.backToList')}
            </Button>
          </Link>
        }
      />
    )
  }

  const opportunityColumns = [
    { title: t('pages.customer.detail.colOpportunityName'), dataIndex: 'name' },
    {
      title: t('pages.customer.detail.colStatus'),
      dataIndex: 'status',
      render: (s: string) => (s === 'ACTIVE' ? <Tag color="blue">{t('pages.opportunity.list.active')}</Tag> : <Tag>{t('pages.opportunity.list.archived')}</Tag>),
    },
    { title: t('pages.customer.detail.colSalesCount'), dataIndex: 'salesOpportunityCount' },
  ]

  const healthLevel = (level?: string) => {
    if (level === 'GREEN') return { color: '#3f8600', label: t('pages.customer.detail.healthGreen') }
    if (level === 'YELLOW') return { color: '#d48806', label: t('pages.customer.detail.healthYellow') }
    return { color: '#cf1322', label: t('pages.customer.detail.healthRed') }
  }

  const orderColumns = [
    { title: t('pages.customer.detail.colOrderNo'), dataIndex: 'orderNo' },
    { title: t('pages.customer.detail.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.customer.detail.colAmount'),
      dataIndex: 'amount',
      render: (v: number) => formatAmount(v),
    },
    {
      title: t('pages.customer.detail.colStatus'),
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, { c: string; l: string }> = {
          PENDING: { c: 'orange', l: t('pages.customer.detail.orderPending') },
          PARTIAL: { c: 'gold', l: t('pages.customer.detail.orderPartial') },
          PAID: { c: 'green', l: t('pages.customer.detail.orderPaid') },
        }
        const m = map[s] ?? { c: 'default', l: s }
        return <Tag color={m.c}>{m.l}</Tag>
      },
    },
  ]

  const paymentColumns = [
    { title: t('pages.customer.detail.colOrderNo'), dataIndex: 'orderNo' },
    { title: t('pages.customer.detail.colReceivable'), dataIndex: 'totalPlan', render: (v: number) => formatAmount(v) },
    { title: t('pages.customer.detail.colPaid'), dataIndex: 'paid', render: (v: number) => formatAmount(v) },
    { title: t('pages.customer.detail.colOverdue'), dataIndex: 'overdue', render: (v: number) => formatAmount(v) },
  ]

  const contractColumns = [
    { title: t('pages.customer.detail.colContractNo'), dataIndex: 'contractNo' },
    { title: t('pages.customer.detail.colTitle'), dataIndex: 'title' },
    { title: t('pages.customer.detail.colAmount'), dataIndex: 'amount', render: (v: number) => formatAmount(v) },
    {
      title: t('pages.customer.detail.colStatus'),
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, string> = {
          DRAFT: t('common.status.draft'),
          PENDING_APPROVAL: t('common.status.pending'),
          APPROVED: t('common.status.approved'),
          EFFECTIVE: t('common.status.effective'),
          COMPLETED: t('common.status.completed'),
          TERMINATED: t('common.status.terminated'),
        }
        return <Tag>{map[s] ?? s}</Tag>
      },
    },
  ]

  const ticketColumns = [
    { title: t('pages.customer.detail.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.customer.detail.colPriority'),
      dataIndex: 'priority',
      render: (s: string) => {
        const map: Record<string, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' }
        return (
          <Tag color={s === 'URGENT' || s === 'HIGH' ? 'red' : s === 'MEDIUM' ? 'orange' : 'default'}>
            {map[s] ?? s}
          </Tag>
        )
      },
    },
    {
      title: t('pages.customer.detail.colStatus'),
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, string> = {
          OPEN: t('pages.customer.detail.ticketOpen'),
          IN_PROGRESS: t('pages.customer.detail.ticketInProgress'),
          RESOLVED: t('pages.customer.detail.ticketResolved'),
          CLOSED: t('pages.customer.detail.ticketClosed'),
        }
        return <Tag color={s === 'OPEN' ? 'orange' : s === 'CLOSED' ? 'green' : 'blue'}>{map[s] ?? s}</Tag>
      },
    },
    {
      title: 'SLA',
      dataIndex: 'slaStatus',
      render: (s?: string) => {
        const map: Record<string, string> = {
          NORMAL: t('pages.customer.detail.slaNormal'),
          WARNING: t('pages.customer.detail.slaWarning'),
          OVERDUE: t('pages.customer.detail.slaOverdue'),
        }
        return <Tag color={s === 'OVERDUE' ? 'red' : s === 'WARNING' ? 'orange' : 'default'}>{s ? map[s] ?? s : '-'}</Tag>
      },
    },
  ]

  return (
    <div>
      <Link to="/customers" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.customer.detail.backToList')}
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0 }}>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.name} <Typography.Text type="secondary" style={{ fontSize: 13 }}>#{data.id}</Typography.Text>
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {data.company} · {data.status === 'ACTIVE' ? t('pages.customer.detail.active') : t('pages.customer.detail.inactive')}
            {data.ownerName
              ? ` · ${t('pages.customer.detail.ownerPrefix')}${data.ownerName}`
              : ` · ${t('pages.customer.detail.pool')}`}
          </Typography.Text>
          <div style={{ marginTop: 6, display: 'flex', gap: 14, flexWrap: 'wrap' }}>
            {data.phone && (
              <a href={`tel:${data.phone}`} style={{ fontSize: 13, color: '#1677ff' }}>
                <PhoneOutlined /> {data.phone}
              </a>
            )}
            {data.email && (
              <a href={`mailto:${data.email}`} style={{ fontSize: 13, color: '#1677ff' }}>
                <MailOutlined /> {data.email}
              </a>
            )}
            {data.customer360?.health?.level && (
              <Tag
                color={
                  data.customer360.health.level === 'GREEN'
                    ? 'green'
                    : data.customer360.health.level === 'YELLOW'
                      ? 'gold'
                      : 'red'
                }
              >
                {t('pages.customer.detail.healthScore')} {healthLevel(data.customer360.health.level).label}
              </Tag>
            )}
          </div>
        </div>
        <Space>
          <Button icon={<EditOutlined />} onClick={openEdit}>
            {t('pages.customer.list.edit')}
          </Button>
          {canShare && (
            <Button icon={<ShareAltOutlined />} onClick={() => void openShare()}>
              {t('common.button.share')}
            </Button>
          )}
        </Space>
      </div>

      <Card
        title={t('pages.customer.detail.basicInfo')}
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
      >
        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
          <Descriptions.Item label={t('pages.customer.list.formName')}>{data.name}</Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formCompany')}>{data.company}</Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formContact')}>{data.contactPerson ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formPhone')}>
            {data.phone ? <a href={`tel:${data.phone}`}>{data.phone}</a> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formEmail')}>
            {data.email ? <a href={`mailto:${data.email}`}>{data.email}</a> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formAddress')}>{data.address ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.formRemark')} span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.customer.list.colStatus')}>
            {data.status === 'ACTIVE' ? <Tag color="green">{t('common.status.active')}</Tag> : <Tag>{t('common.status.inactive')}</Tag>}
          </Descriptions.Item>
          {data.customFieldValues?.map((cf) => (
            <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `${t('pages.customer.detail.fieldPrefix')}${cf.fieldId}`}>
              {cf.value || '-'}
            </Descriptions.Item>
          ))}
        </Descriptions>
      </Card>

      {/* 031：客户标签 */}
      <Card
        title={t('pages.customer.detail.tags')}
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        extra={
          <Button size="small" onClick={() => void onEditTags()}>
            {t('pages.customer.detail.editTags')}
          </Button>
        }
      >
        {customerTags.length === 0 ? (
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {t('pages.customer.detail.noTags')}
          </Typography.Text>
        ) : (
          customerTags.map((tag) => (
            <Tag key={tag.id} color={tag.color} style={{ marginBottom: 4 }}>
              {tag.name}
            </Tag>
          ))
        )}
      </Card>

      {data.customer360 && (
        <Card
          title={t('pages.customer.detail.customer360')}
          style={{ marginBottom: 16, borderRadius: 10 }}
          styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        >
          <div style={{ display: 'flex', gap: 24, marginBottom: 16, flexWrap: 'wrap' }}>
            {/* 健康度评分 */}
            <div style={{ minWidth: 200, flex: 1 }}>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                {t('pages.customer.detail.healthScore')}
              </Typography.Text>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
                <div
                  style={{
                    fontSize: 32,
                    fontWeight: 700,
                    color: healthLevel(data.customer360.health?.level).color,
                  }}
                >
                  {data.customer360.health?.score ?? '-'}
                </div>
                <Tag
                  color={
                    healthLevel(data.customer360.health?.level).color === '#3f8600'
                      ? 'green'
                      : healthLevel(data.customer360.health?.level).color === '#d48806'
                        ? 'gold'
                        : 'red'
                  }
                >
                  {healthLevel(data.customer360.health?.level).label}
                </Tag>
                <Progress
                  percent={data.customer360.health?.score ?? 0}
                  showInfo={false}
                  strokeColor={healthLevel(data.customer360.health?.level).color}
                  style={{ flex: 1, maxWidth: 200 }}
                />
              </div>
              {data.customer360.health?.deductions?.length ? (
                <div style={{ marginTop: 8 }}>
                  {data.customer360.health.deductions.map((d) => (
                    <Typography.Text key={d.dimension} type="secondary" style={{ fontSize: 12, marginRight: 12 }}>
                      {d.dimension} -{d.deduct} pts
                    </Typography.Text>
                  ))}
                </div>
              ) : (
                <Typography.Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>
                  {t('pages.customer.detail.noDeductions')}
                </Typography.Text>
              )}
            </div>
            {/* 金额汇总 */}
            <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
              <Statistic title={t('pages.customer.detail.totalOrder')} value={formatAmount(data.customer360.amountSummary?.totalOrder)} />
              <Statistic title={t('pages.customer.detail.paid')} value={formatAmount(data.customer360.amountSummary?.paid)} valueStyle={{ color: '#3f8600' }} />
              <Statistic title={t('pages.customer.detail.overdue')} value={formatAmount(data.customer360.amountSummary?.dueOverdue)} valueStyle={{ color: '#cf1322' }} />
            </div>
          </div>

          <Tabs
            size="small"
            items={[
              {
                key: 'orders',
                label: t('pages.customer.detail.tabOrders', { count: data.customer360.orders?.length ?? 0 }),
                children: (
                  <Table<OrderBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.orders ?? []}
                    columns={orderColumns as never}
                    pagination={false}
                    locale={{ emptyText: t('pages.customer.detail.emptyOrders') }}
                  />
                ),
              },
              {
                key: 'payments',
                label: t('pages.customer.detail.tabPayments', { count: data.customer360.paymentSummaries?.length ?? 0 }),
                children: (
                  <Table<PaymentSummary>
                    rowKey="orderId"
                    size="small"
                    dataSource={data.customer360.paymentSummaries ?? []}
                    columns={paymentColumns as never}
                    pagination={false}
                    locale={{ emptyText: t('pages.customer.detail.emptyPayments') }}
                  />
                ),
              },
              {
                key: 'contracts',
                label: t('pages.customer.detail.tabContracts', { count: data.customer360.contracts?.length ?? 0 }),
                children: (
                  <Table<ContractBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.contracts ?? []}
                    columns={contractColumns as never}
                    pagination={false}
                    locale={{ emptyText: t('pages.customer.detail.emptyContracts') }}
                  />
                ),
              },
              {
                key: 'tickets',
                label: t('pages.customer.detail.tabTickets', { count: data.customer360.tickets?.length ?? 0 }),
                children: (
                  <Table<TicketBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.tickets ?? []}
                    columns={ticketColumns as never}
                    pagination={false}
                    locale={{ emptyText: t('pages.customer.detail.emptyTickets') }}
                  />
                ),
              },
            ]}
          />
        </Card>
      )}

      <Card
        title={t('pages.customer.detail.relatedOpportunities')}
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' }, body: { padding: 0 } }}
      >
        <Table<OpportunityBrief>
          rowKey="id"
          size="small"
          dataSource={data.opportunities}
          columns={opportunityColumns as never}
          pagination={false}
          locale={{ emptyText: t('pages.customer.detail.emptyOpportunities') }}
        />
      </Card>

      <ContactsCard customerId={customerId} />

      <FollowUpTimeline customerId={customerId} />

      {/* 037：评论协作 */}
      <Card title={t('pages.customer.detail.comments')} style={{ marginBottom: 16, borderRadius: 10 }} styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}>
        <CommentSection entityType="CUSTOMER" entityId={customerId} />
      </Card>

      <Modal
        title={t('pages.customer.detail.shareModal', { name: data.name })}
        open={shareOpen}
        onOk={() => void onShare()}
        onCancel={() => setShareOpen(false)}
        okText={t('common.button.share')}
        destroyOnClose
      >
        <Form form={shareForm} name="shareForm" layout="vertical">
          <Form.Item
            name="sharedToUserId"
            label={t('pages.customer.detail.shareTo')}
            rules={[{ required: true, message: t('pages.customer.detail.msgUserRequired') }]}
          >
            <Select showSearch optionFilterProp="label" placeholder={t('pages.customer.detail.shareTo')} options={userOptions} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 优化：详情页编辑客户 */}
      <Modal
        title={t('pages.customer.detail.editModal', { name: data.name })}
        open={editOpen}
        onOk={() => void onSaveEdit()}
        onCancel={() => setEditOpen(false)}
        okText={t('common.button.save')}
        confirmLoading={editSaving}
        destroyOnClose
        width={520}
      >
        <Form form={editForm} name="editForm" layout="vertical">
          <Form.Item name="name" label={t('pages.customer.list.formName')} rules={[{ required: true, message: t('pages.customer.list.msgNameRequired') }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="company" label={t('pages.customer.list.formCompany')} rules={[{ required: true, message: t('pages.customer.list.msgCompanyRequired') }]}>
            <Input maxLength={100} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="contactPerson" label={t('pages.customer.list.formContact')} style={{ flex: 1 }}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="phone" label={t('pages.customer.list.formPhone')} style={{ flex: 1 }}>
              <Input maxLength={20} />
            </Form.Item>
          </div>
          <Form.Item name="email" label={t('pages.customer.list.formEmail')} rules={[{ type: 'email', message: t('common.message.invalid_email') }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="address" label={t('pages.customer.list.formAddress')}>
            <Input maxLength={200} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="status" label={t('pages.customer.list.colStatus')} style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 'ACTIVE', label: t('common.status.active') },
                  { value: 'INACTIVE', label: t('common.status.inactive') },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item name="remark" label={t('pages.customer.list.formRemark')}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 031：编辑客户标签 */}
      <Modal
        title={`${t('pages.customer.detail.editTags')}「${data.name}」`}
        open={tagOpen}
        onOk={() => void onSaveTags()}
        onCancel={() => setTagOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder={t('pages.customer.detail.editTags')}
          value={selectedTagIds}
          onChange={setSelectedTagIds}
          options={tagOptions}
        />
      </Modal>
    </div>
  )
}
