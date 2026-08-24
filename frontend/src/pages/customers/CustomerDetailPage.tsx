import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Modal,
  Progress,
  Result,
  Select,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, ShareAltOutlined } from '@ant-design/icons'
import { useCustomerDetail } from '../../hooks/useCustomers'
import { shareCustomer } from '../../services/customerShareService'
import { fetchUsers } from '../../services/userService'
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
  const { id } = useParams()
  const customerId = Number(id)
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const [shareOpen, setShareOpen] = useState(false)
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [shareForm] = Form.useForm<{ sharedToUserId: number }>()
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
    message.success('标签已更新')
    setTagOpen(false)
    void loadTags()
  }

  const onShare = async () => {
    const values = await shareForm.validateFields()
    try {
      await shareCustomer(customerId, values.sharedToUserId)
      message.success('已共享')
      setShareOpen(false)
    } catch (err) {
      message.error(extractErrorMessage(err, '共享失败'))
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
        title="客户不存在或已被删除"
        extra={
          <Link to="/customers">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              返回客户列表
            </Button>
          </Link>
        }
      />
    )
  }

  const opportunityColumns = [
    { title: '商机名称', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (s: string) => (s === 'ACTIVE' ? <Tag color="blue">进行中</Tag> : <Tag>已归档</Tag>),
    },
    { title: '销售机会数', dataIndex: 'salesOpportunityCount' },
  ]

  const healthLevel = (level?: string) => {
    if (level === 'GREEN') return { color: '#3f8600', label: '健康' }
    if (level === 'YELLOW') return { color: '#d48806', label: '关注' }
    return { color: '#cf1322', label: '风险' }
  }

  const orderColumns = [
    { title: '订单号', dataIndex: 'orderNo' },
    { title: '标题', dataIndex: 'title' },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      render: (v: number) => formatAmount(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, { c: string; l: string }> = {
          PENDING: { c: 'orange', l: '待回款' },
          PARTIAL: { c: 'gold', l: '部分回款' },
          PAID: { c: 'green', l: '已结清' },
        }
        const m = map[s] ?? { c: 'default', l: s }
        return <Tag color={m.c}>{m.l}</Tag>
      },
    },
  ]

  const paymentColumns = [
    { title: '订单号', dataIndex: 'orderNo' },
    { title: '应收（元）', dataIndex: 'totalPlan', render: (v: number) => formatAmount(v) },
    { title: '已回款（元）', dataIndex: 'paid', render: (v: number) => formatAmount(v) },
    { title: '逾期（元）', dataIndex: 'overdue', render: (v: number) => formatAmount(v) },
  ]

  const contractColumns = [
    { title: '合同编号', dataIndex: 'contractNo' },
    { title: '标题', dataIndex: 'title' },
    { title: '金额（元）', dataIndex: 'amount', render: (v: number) => formatAmount(v) },
    {
      title: '状态',
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, string> = {
          DRAFT: '草稿',
          PENDING_APPROVAL: '审批中',
          APPROVED: '已批准',
          EFFECTIVE: '生效中',
          COMPLETED: '已完成',
          TERMINATED: '已终止',
        }
        return <Tag>{map[s] ?? s}</Tag>
      },
    },
  ]

  const ticketColumns = [
    { title: '标题', dataIndex: 'title' },
    {
      title: '优先级',
      dataIndex: 'priority',
      render: (s: string) => {
        const map: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
        return (
          <Tag color={s === 'URGENT' || s === 'HIGH' ? 'red' : s === 'MEDIUM' ? 'orange' : 'default'}>
            {map[s] ?? s}
          </Tag>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (s: string) => {
        const map: Record<string, string> = { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }
        return <Tag color={s === 'OPEN' ? 'orange' : s === 'CLOSED' ? 'green' : 'blue'}>{map[s] ?? s}</Tag>
      },
    },
    {
      title: 'SLA',
      dataIndex: 'slaStatus',
      render: (s?: string) => {
        const map: Record<string, string> = { NORMAL: '正常', WARNING: '告警', OVERDUE: '超时' }
        return <Tag color={s === 'OVERDUE' ? 'red' : s === 'WARNING' ? 'orange' : 'default'}>{s ? map[s] ?? s : '-'}</Tag>
      },
    },
  ]

  return (
    <div>
      <Link to="/customers" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回客户列表
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.name}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {data.company} · {data.status === 'ACTIVE' ? '启用中' : '已停用'}
            {data.ownerName ? ` · 归属：${data.ownerName}` : ' · 公海'}
          </Typography.Text>
        </div>
        {canShare && (
          <Button icon={<ShareAltOutlined />} onClick={() => void openShare()}>
            共享给...
          </Button>
        )}
      </div>

      <Card
        title="基本信息"
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="客户名称">{data.name}</Descriptions.Item>
          <Descriptions.Item label="公司">{data.company}</Descriptions.Item>
          <Descriptions.Item label="联系人">{data.contactPerson ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="电话">{data.phone ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{data.email ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="地址">{data.address ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="备注" span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {data.status === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
          </Descriptions.Item>
          {data.customFieldValues?.map((cf) => (
            <Descriptions.Item key={cf.fieldId} label={cf.fieldName ?? `字段#${cf.fieldId}`}>
              {cf.value || '-'}
            </Descriptions.Item>
          ))}
        </Descriptions>
      </Card>

      {/* 031：客户标签 */}
      <Card
        title="标签"
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        extra={
          <Button size="small" onClick={() => void onEditTags()}>
            编辑标签
          </Button>
        }
      >
        {customerTags.length === 0 ? (
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            暂无标签，点击右上角"编辑标签"添加
          </Typography.Text>
        ) : (
          customerTags.map((t) => (
            <Tag key={t.id} color={t.color} style={{ marginBottom: 4 }}>
              {t.name}
            </Tag>
          ))
        )}
      </Card>

      {data.customer360 && (
        <Card
          title="客户 360"
          style={{ marginBottom: 16, borderRadius: 10 }}
          styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}
        >
          <div style={{ display: 'flex', gap: 24, marginBottom: 16, flexWrap: 'wrap' }}>
            {/* 健康度评分 */}
            <div style={{ minWidth: 200, flex: 1 }}>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                健康度评分
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
                      {d.dimension} -{d.deduct} 分
                    </Typography.Text>
                  ))}
                </div>
              ) : (
                <Typography.Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>
                  暂无失分项
                </Typography.Text>
              )}
            </div>
            {/* 金额汇总 */}
            <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
              <Statistic title="累计订单（元）" value={formatAmount(data.customer360.amountSummary?.totalOrder)} />
              <Statistic title="已回款（元）" value={formatAmount(data.customer360.amountSummary?.paid)} valueStyle={{ color: '#3f8600' }} />
              <Statistic title="逾期未回款（元）" value={formatAmount(data.customer360.amountSummary?.dueOverdue)} valueStyle={{ color: '#cf1322' }} />
            </div>
          </div>

          <Tabs
            size="small"
            items={[
              {
                key: 'orders',
                label: `订单（${data.customer360.orders?.length ?? 0}）`,
                children: (
                  <Table<OrderBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.orders ?? []}
                    columns={orderColumns as never}
                    pagination={false}
                    locale={{ emptyText: '暂无订单' }}
                  />
                ),
              },
              {
                key: 'payments',
                label: `回款（${data.customer360.paymentSummaries?.length ?? 0}）`,
                children: (
                  <Table<PaymentSummary>
                    rowKey="orderId"
                    size="small"
                    dataSource={data.customer360.paymentSummaries ?? []}
                    columns={paymentColumns as never}
                    pagination={false}
                    locale={{ emptyText: '暂无回款记录' }}
                  />
                ),
              },
              {
                key: 'contracts',
                label: `合同（${data.customer360.contracts?.length ?? 0}）`,
                children: (
                  <Table<ContractBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.contracts ?? []}
                    columns={contractColumns as never}
                    pagination={false}
                    locale={{ emptyText: '暂无合同' }}
                  />
                ),
              },
              {
                key: 'tickets',
                label: `工单（${data.customer360.tickets?.length ?? 0}）`,
                children: (
                  <Table<TicketBrief>
                    rowKey="id"
                    size="small"
                    dataSource={data.customer360.tickets ?? []}
                    columns={ticketColumns as never}
                    pagination={false}
                    locale={{ emptyText: '暂无工单' }}
                  />
                ),
              },
            ]}
          />
        </Card>
      )}

      <Card
        title="关联商机"
        style={{ marginBottom: 16, borderRadius: 10 }}
        styles={{ header: { borderBottom: '1px solid #f0f0f0' }, body: { padding: 0 } }}
      >
        <Table<OpportunityBrief>
          rowKey="id"
          size="small"
          dataSource={data.opportunities}
          columns={opportunityColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无关联商机' }}
        />
      </Card>

      <ContactsCard customerId={customerId} />

      <FollowUpTimeline customerId={customerId} />

      {/* 037：评论协作 */}
      <Card title="评论" style={{ marginBottom: 16, borderRadius: 10 }} styles={{ header: { borderBottom: '1px solid #f0f0f0' } }}>
        <CommentSection entityType="CUSTOMER" entityId={customerId} />
      </Card>

      <Modal
        title={`共享客户「${data.name}」`}
        open={shareOpen}
        onOk={() => void onShare()}
        onCancel={() => setShareOpen(false)}
        okText="共享"
        destroyOnClose
      >
        <Form form={shareForm} name="shareForm" layout="vertical">
          <Form.Item
            name="sharedToUserId"
            label="共享给"
            rules={[{ required: true, message: '请选择用户' }]}
          >
            <Select showSearch optionFilterProp="label" placeholder="选择用户" options={userOptions} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 031：编辑客户标签 */}
      <Modal
        title={`编辑标签「${data.name}」`}
        open={tagOpen}
        onOk={() => void onSaveTags()}
        onCancel={() => setTagOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="选择标签"
          value={selectedTagIds}
          onChange={setSelectedTagIds}
          options={tagOptions}
        />
      </Modal>
    </div>
  )
}
