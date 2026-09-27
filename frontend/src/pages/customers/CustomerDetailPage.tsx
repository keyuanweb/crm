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
import {
  ArrowLeftOutlined,
  EditOutlined,
  MailOutlined,
  PhoneOutlined,
  ShareAltOutlined,
  BulbOutlined,
  FileTextOutlined,
  ShoppingCartOutlined,
  CustomerServiceOutlined,
} from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { useCustomerDetail } from '../../hooks/useCustomers'
import { shareCustomer } from '../../services/customerShareService'
import { fetchUsers } from '../../services/userService'
import { updateCustomer, type CustomerPayload } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { formatAmount } from '../../types/opportunity'
import { useAuthStore } from '../../store/authStore'
import { hasPerm } from '../../hooks/usePermission'
import { PERMS } from '../../constants/permissions'
import AiGenerateButton from '../../components/AiGenerateButton'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import CommentSection from '../../components/CommentSection'
import type {
  ContractBrief,
  OpportunityBrief,
  OrderBrief,
  PaymentSummary,
  TicketBrief,
} from '../../types/customer'
import { FormGrid, StatCard, StatusTag, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

export default function CustomerDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const customerId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  // 086：共享客户在后端是**两道**闸门，前端必须与它逐字对上：
  //   ① 码：POST/DELETE /customer-shares 挂 `customer:update`（CustomerShareController.java:52,60）；
  //   ② 数据归属：CustomerShareService.share 里 `if (!isAdmin && !ownerId.equals(currentUserId)) throw FORBIDDEN`。
  //
  // 改造前此处只写了 ②，注释的依据是"字典里也还没有可用的权限码"——那句话已过时：1.5 批 3 起该码存在，
  // 持有者是 ADMIN / SALES / SUPPORT / SALES_MANAGER / SALES_REP（**不是仅 ADMIN**，
  // 见 CustomerShareController.java:36-37 的类注释）。只写 ② 的后果：
  // 一个**拥有客户但没有 `customer:update`** 的角色（如 VIEWER）会看到「共享」按钮却必然 403。
  const canManageShare = hasPerm(PERMS.customerUpdate, user)
  // ② 的 ADMIN 例外必须保留：后端允许管理员共享任何人的客户，而 `hasPerm` 无法表达
  // "是 ADMIN 但不是 owner"这一支（它对 ADMIN 与持码者都返回 true，两者被合并了）。
  const isAdmin = user?.role === 'ADMIN'
  // 104 P1：AI 邮件草稿。`ai:generate` 对预置角色**零授予**（`AiPermissionGrantIT`），故实际只有
  // ADMIN 看得到这个按钮——这正是 FR-020 那份决定在界面上的形状，前端不再另设一套判据。
  const canGenerateDraft = hasPerm(PERMS.aiGenerate, user)
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
        // 共享对象排除整个客服族（SUPPORT / SUPPORT_MANAGER / SUPPORT_AGENT）：改造前写的是
        // `u.role !== 'SUPPORT'`，只认那一个字面量角色名，081 之后客服改名成 SUPPORT_MANAGER /
        // SUPPORT_AGENT，这个判断就再也筛不掉客服了。按前缀匹配才对得上本意。
        // 后端没有对应规则（CustomerShareService.share 只校验「归属者或 ADMIN」，不看被共享人的角色），
        // 这里只是下拉框的建议性过滤，不是强制。
        .filter((u) => !u.role.startsWith('SUPPORT') && u.id !== user?.id)
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

  // 两道闸门缺一不可（见上方 :59-70 的注释）：码 ∧（归属者 ∨ 管理员）。
  const canShare = !!data && canManageShare && (isAdmin || data.ownerId === user?.id)

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
      render: (s: string) => (s === 'ACTIVE' ? <StatusTag type="info">{t('pages.opportunity.list.active')}</StatusTag> : <StatusTag>{t('pages.opportunity.list.archived')}</StatusTag>),
    },
    { title: t('pages.customer.detail.colSalesCount'), dataIndex: 'salesOpportunityCount' },
  ]

  const healthLevel = (level?: string) => {
    if (level === 'GREEN') return { color: 'var(--color-success)', label: t('pages.customer.detail.healthGreen') }
    if (level === 'YELLOW') return { color: 'var(--color-warning)', label: t('pages.customer.detail.healthYellow') }
    return { color: 'var(--color-danger)', label: t('pages.customer.detail.healthRed') }
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
        const map: Record<string, { c: StatusTagType; l: string }> = {
          PENDING: { c: 'warning', l: t('pages.customer.detail.orderPending') },
          PARTIAL: { c: 'info', l: t('pages.customer.detail.orderPartial') },
          PAID: { c: 'success', l: t('pages.customer.detail.orderPaid') },
        }
        const m = map[s] ?? { c: 'default', l: s }
        return <StatusTag type={m.c}>{m.l}</StatusTag>
      },
    },
  ]

  type StatusTagType = 'success' | 'warning' | 'danger' | 'info' | 'default'

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
        const map: Record<string, StatusTagType> = {
          DRAFT: 'default',
          PENDING_APPROVAL: 'info',
          APPROVED: 'info',
          EFFECTIVE: 'success',
          COMPLETED: 'info',
          TERMINATED: 'danger',
        }
        return <StatusTag type={map[s] ?? 'default'}>{s}</StatusTag>
      },
    },
  ]

  const ticketColumns = [
    { title: t('pages.customer.detail.colTitle'), dataIndex: 'title' },
    {
      title: t('pages.customer.detail.colPriority'),
      dataIndex: 'priority',
      render: (s: string) => {
        const map: Record<string, StatusTagType> = { LOW: 'default', MEDIUM: 'warning', HIGH: 'danger', URGENT: 'danger' }
        return (
          <StatusTag type={map[s] ?? 'default'}>
            {s}
          </StatusTag>
        )
      },
    },
    {
      title: t('pages.customer.detail.colStatus'),
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, StatusTagType> = {
          OPEN: 'warning',
          IN_PROGRESS: 'info',
          RESOLVED: 'success',
          CLOSED: 'default',
        }
        return <StatusTag type={map[s] ?? 'default'}>{s}</StatusTag>
      },
    },
    {
      title: 'SLA',
      dataIndex: 'slaStatus',
      render: (s?: string) => {
        const map: Record<string, StatusTagType> = {
          NORMAL: 'success',
          WARNING: 'warning',
          OVERDUE: 'danger',
        }
        return s ? <StatusTag type={map[s] ?? 'default'}>{s}</StatusTag> : <StatusTag>-</StatusTag>
      },
    },
  ]

  // 统计卡片数据
  const statsCards = [
    {
      value: data.opportunities?.length ?? 0,
      label: t('pages.customer.detail.tabOpportunities', { count: 0 }),
      icon: <BulbOutlined />,
    },
    {
      value: data.customer360?.amountSummary?.totalOrder ? formatAmount(data.customer360.amountSummary.totalOrder) : '-',
      label: t('pages.customer.detail.totalContract'),
      icon: <FileTextOutlined />,
    },
    {
      value: data.customer360?.orders?.length ?? 0,
      label: t('pages.customer.detail.tabOrders', { count: 0 }),
      icon: <ShoppingCartOutlined />,
    },
    {
      value: data.customer360?.tickets?.length ?? 0,
      label: t('pages.customer.detail.tabTickets', { count: 0 }),
      icon: <CustomerServiceOutlined />,
    },
  ]

  const tabItems = [
    {
      key: 'basic',
      label: t('pages.customer.detail.tabBasic'),
      children: (
        <Card bordered={false} style={{ background: 'var(--color-bg-page)' }}>
          <Descriptions
            column={{ xs: 1, sm: 2, md: 3 }}
            bordered
            size="small"
            layout="horizontal"
          >
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
            <Descriptions.Item label={t('pages.customer.list.colStatus')}>
              {data.status === 'ACTIVE' ? <StatusTag type="success">{t('common.status.active')}</StatusTag> : <StatusTag type="default">{t('common.status.inactive')}</StatusTag>}
            </Descriptions.Item>
            <Descriptions.Item label={t('pages.customer.list.formRemark')} span={3}>
              {data.remark ?? '-'}
            </Descriptions.Item>
            {data.customFieldValues?.map((cf) => (
              <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `${t('pages.customer.detail.fieldPrefix')}${cf.fieldId}`}>
                {cf.value || '-'}
              </Descriptions.Item>
            ))}
          </Descriptions>
        </Card>
      ),
    },
    {
      key: 'tags',
      label: t('pages.customer.detail.tabTags'),
      children: (
        <Card
          bordered={false}
          style={{ background: 'var(--color-bg-page)' }}
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
            <Space wrap>
              {customerTags.map((tag) => (
                <Tag key={tag.id} color={tag.color} style={{ padding: '2px 12px', borderRadius: 'var(--radius-full)' }}>
                  {tag.name}
                </Tag>
              ))}
            </Space>
          )}
        </Card>
      ),
    },
  ]

  // 添加 360 Tab（如果有数据）
  if (data.customer360) {
    tabItems.push({
      key: 'overview',
      label: t('pages.customer.detail.tabOverview'),
      children: (
        <Card bordered={false} style={{ background: 'var(--color-bg-page)' }}>
          {/* 健康度 */}
          <div style={{ marginBottom: 24 }}>
            <Typography.Text type="secondary" style={{ fontSize: 13, marginBottom: 8, display: 'block' }}>
              {t('pages.customer.detail.healthScore')}
            </Typography.Text>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{ fontSize: 36, fontWeight: 700, color: healthLevel(data.customer360.health?.level).color }}>
                {data.customer360.health?.score ?? '-'}
              </div>
              <StatusTag type={data.customer360.health?.level === 'GREEN' ? 'success' : data.customer360.health?.level === 'YELLOW' ? 'warning' : 'danger'}>
                {healthLevel(data.customer360.health?.level).label}
              </StatusTag>
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
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 24 }}>
            <Statistic
              title={t('pages.customer.detail.totalOrder')}
              value={formatAmount(data.customer360.amountSummary?.totalOrder)}
              valueStyle={{ fontSize: 18, fontWeight: 600 }}
            />
            <Statistic
              title={t('pages.customer.detail.paid')}
              value={formatAmount(data.customer360.amountSummary?.paid)}
              valueStyle={{ fontSize: 18, fontWeight: 600, color: 'var(--color-success)' }}
            />
            <Statistic
              title={t('pages.customer.detail.overdue')}
              value={formatAmount(data.customer360.amountSummary?.dueOverdue)}
              valueStyle={{ fontSize: 18, fontWeight: 600, color: 'var(--color-danger)' }}
            />
          </div>

          {/* 子 Tab：订单、付款、合同、工单 */}
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
                    bordered
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
                    bordered
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
                    bordered
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
                    bordered
                  />
                ),
              },
            ]}
          />
        </Card>
      ),
    })
  }

  // 添加商机 Tab
  tabItems.push({
    key: 'opportunities',
    label: t('pages.customer.detail.tabOpportunities', { count: data.opportunities?.length ?? 0 }),
    children: (
      <Table<OpportunityBrief>
        rowKey="id"
        size="small"
        dataSource={data.opportunities}
        columns={opportunityColumns as never}
        pagination={false}
        locale={{ emptyText: t('pages.customer.detail.emptyOpportunities') }}
        bordered
      />
    ),
  })

  return (
    <div>
      {/* 返回按钮 */}
      <Link to="/customers" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }}>
          {t('pages.customer.detail.backToList')}
        </Button>
      </Link>

      {/* 顶部信息栏 */}
      <Card
        bordered={false}
        style={{
          borderRadius: 'var(--radius-lg)',
          marginBottom: 20,
          background: 'linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-bg-card) 100%)',
          border: '1px solid var(--color-border-light)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, minWidth: 0 }}>
            {/* 客户图标 */}
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: 'var(--radius-lg)',
                background: 'var(--color-primary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#fff',
                fontSize: 24,
                flexShrink: 0,
              }}
            >
              🏢
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {data.name}
                </Typography.Title>
                <StatusTag type={data.status === 'ACTIVE' ? 'success' : 'default'}>
                  {data.status === 'ACTIVE' ? t('pages.customer.detail.active') : t('pages.customer.detail.inactive')}
                </StatusTag>
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 4 }}>
                #{data.id} · {data.company}
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                {data.ownerName
                  ? `${t('pages.customer.detail.ownerPrefix')}${data.ownerName}`
                  : t('pages.customer.detail.pool')}
              </Typography.Text>
              <div style={{ marginTop: 6, display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                {data.phone && (
                  <a href={`tel:${data.phone}`} style={{ fontSize: 13, color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <PhoneOutlined /> {data.phone}
                  </a>
                )}
                {data.email && (
                  <a href={`mailto:${data.email}`} style={{ fontSize: 13, color: 'var(--color-primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <MailOutlined /> {data.email}
                  </a>
                )}
                {data.customer360?.health?.level && (
                  <StatusTag type={data.customer360.health.level === 'GREEN' ? 'success' : data.customer360.health.level === 'YELLOW' ? 'warning' : 'danger'}>
                    {t('pages.customer.detail.healthScore')} {healthLevel(data.customer360.health.level).label}
                  </StatusTag>
                )}
              </div>
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
            {canGenerateDraft && <AiGenerateButton customerId={customerId} />}
          </Space>
        </div>
      </Card>

      {/* 统计卡片行 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 20 }}>
        {statsCards.map((card, index) => (
          <StatCard
            key={index}
            value={card.value}
            label={card.label}
            icon={card.icon}
          />
        ))}
      </div>

      {/* Tab 内容区 */}
      <Tabs
        size="large"
        tabPosition="top"
        items={tabItems}
        style={{ borderRadius: 'var(--radius-lg)' }}
      />

      {/* 跟进记录 */}
      <FollowUpTimeline customerId={customerId} />

      {/* 037：评论协作 */}
      <Card
        title={t('pages.customer.detail.comments')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)', marginTop: 20 }}
      >
        <CommentSection entityType="CUSTOMER" entityId={customerId} />
      </Card>

      {/* 分享弹窗 */}
      <Modal
        title={t('pages.customer.detail.shareModal', { name: data.name })}
        open={shareOpen}
        onOk={() => void onShare()}
        onCancel={() => setShareOpen(false)}
        okText={t('common.button.share')}
        destroyOnClose
        width={480}
        styles={{ body: { padding: '20px 24px' } }}
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

      {/* 编辑客户弹窗 */}
      <Modal
        title={t('pages.customer.detail.editModal', { name: data.name })}
        open={editOpen}
        onOk={() => void onSaveEdit()}
        onCancel={() => setEditOpen(false)}
        okText={t('common.button.save')}
        confirmLoading={editSaving}
        destroyOnClose
        width={520}
        styles={{ body: { padding: '20px 24px' } }}
      >
        <Form form={editForm} name="editForm" layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="name" label={t('pages.customer.list.formName')} rules={[{ required: true, message: t('pages.customer.list.msgNameRequired') }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="company" label={t('pages.customer.list.formCompany')} rules={[{ required: true, message: t('pages.customer.list.msgCompanyRequired') }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="contactPerson" label={t('pages.customer.list.formContact')}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="phone" label={t('pages.customer.list.formPhone')}>
              <Input maxLength={20} />
            </Form.Item>
            <Form.Item name="email" label={t('pages.customer.list.formEmail')} rules={[{ type: 'email', message: t('common.message.invalid_email') }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="address" label={t('pages.customer.list.formAddress')}>
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item name="status" label={t('pages.customer.list.colStatus')}>
              <Select
                options={[
                  { value: 'ACTIVE', label: t('common.status.active') },
                  { value: 'INACTIVE', label: t('common.status.inactive') },
                ]}
              />
            </Form.Item>
          </FormGrid>
          <Form.Item name="remark" label={t('pages.customer.list.formRemark')}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑客户标签弹窗 */}
      <Modal
        title={`${t('pages.customer.detail.editTags')}「${data.name}」`}
        open={tagOpen}
        onOk={() => void onSaveTags()}
        onCancel={() => setTagOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        styles={{ body: { padding: '20px 24px' } }}
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
