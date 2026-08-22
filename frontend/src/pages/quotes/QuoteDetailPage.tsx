import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Result,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveQuote,
  exportQuotePdf,
  fetchQuote,
  rejectQuote,
  submitQuote,
} from '../../services/quoteService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import {
  QUOTE_STATUS_COLORS,
  QUOTE_STATUS_LABELS,
  type QuoteStatus,
} from '../../types/quote'
import type { QuoteItem } from '../../types/quote'

export default function QuoteDetailPage() {
  const { id } = useParams()
  const quoteId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectForm] = Form.useForm<{ reason: string }>()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['quote', quoteId],
    queryFn: () => fetchQuote(quoteId),
    enabled: Number.isFinite(quoteId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['quote', quoteId] })
    void refetch()
  }

  const onAction = async (fn: () => Promise<unknown>, successMsg: string) => {
    try {
      await fn()
      message.success(successMsg)
      invalidate()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onReject = async () => {
    const values = await rejectForm.validateFields()
    await onAction(() => rejectQuote(quoteId, values.reason), '已拒绝')
    setRejectOpen(false)
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title="报价单不存在或已被删除"
        extra={
          <Link to="/quotes">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              返回报价单列表
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status as QuoteStatus
  const canSubmit = status === 'DRAFT' || status === 'REJECTED'
  const canApprove = isAdmin && status === 'PENDING_APPROVAL'

  const itemColumns = [
    { title: '产品', dataIndex: 'productName' },
    { title: '数量', dataIndex: 'quantity' },
    {
      title: '单价（元）',
      dataIndex: 'unitPrice',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    {
      title: '折扣',
      dataIndex: 'discount',
      render: (v: number) => `${Math.round(v * 100)}%`,
    },
    {
      title: '小计（元）',
      dataIndex: 'lineTotal',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
  ]

  return (
    <div>
      <Link to="/quotes" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回报价单列表
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.quoteNo}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            创建时间：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
          </Typography.Text>
        </div>
        <Space>
          {canSubmit && (
            <Button
              type="primary"
              onClick={() => onAction(() => submitQuote(quoteId), '已提交审批')}
            >
              提交审批
            </Button>
          )}
          {canApprove && (
            <>
              <Button
                type="primary"
                onClick={() => onAction(() => approveQuote(quoteId), '审批已通过')}
              >
                审批通过
              </Button>
              <Button danger onClick={() => { rejectForm.resetFields(); setRejectOpen(true) }}>
                拒绝
              </Button>
            </>
          )}
          <Button icon={<DownloadOutlined />} onClick={() => void onAction(() => exportQuotePdf(quoteId), 'PDF 已导出')}>
            导出 PDF
          </Button>
        </Space>
      </div>

      <Card title="基本信息" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="状态">
            <Tag color={QUOTE_STATUS_COLORS[status]}>{QUOTE_STATUS_LABELS[status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户">
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label="有效期">{data.validUntil ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="总额（元）">
            <Typography.Text strong>¥ {(data.totalAmount / 100).toLocaleString('zh-CN')}</Typography.Text>
          </Descriptions.Item>
          {data.approvedAt && (
            <Descriptions.Item label="审批时间">
              {data.approvedAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.rejectReason && (
            <Descriptions.Item label="拒绝意见" span={2}>
              <Typography.Text type="danger">{data.rejectReason}</Typography.Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="备注" span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title="产品明细"
        style={{ borderRadius: 10 }}
        bodyStyle={{ padding: 0 }}
      >
        <Table<QuoteItem>
          rowKey="id"
          size="middle"
          dataSource={data.items ?? []}
          columns={itemColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无产品行' }}
        />
      </Card>

      <Modal
        title="拒绝报价"
        open={rejectOpen}
        onOk={() => void onReject()}
        onCancel={() => setRejectOpen(false)}
        okText="确认拒绝"
        destroyOnClose
      >
        <Form form={rejectForm} name="rejectForm" layout="vertical">
          <Form.Item
            name="reason"
            label="拒绝意见"
            rules={[{ required: true, message: '请填写拒绝意见' }]}
          >
            <Input.TextArea rows={3} placeholder="如：价格过高，建议下调 10%" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
