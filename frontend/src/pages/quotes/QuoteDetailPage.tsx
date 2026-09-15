import { useState } from 'react'
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
import { signQuote, fetchQuoteSignature } from '../../services/signatureService'
import SignSection from '../../components/SignSection'
import { useAuthStore } from '../../store/authStore'
import { hasPerm } from '../../hooks/usePermission'
import { PERMS } from '../../constants/permissions'
import {
  QUOTE_STATUS_COLORS,
  type QuoteStatus,
} from '../../types/quote'
import type { QuoteItem } from '../../types/quote'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

export default function QuoteDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const quoteId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
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
      message.error(extractErrorMessage(err, t('pages.quote.detail.msgFailed')))
    }
  }

  const onReject = async () => {
    const values = await rejectForm.validateFields()
    await onAction(() => rejectQuote(quoteId, values.reason), t('pages.quote.detail.msgRejected'))
    setRejectOpen(false)
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title={t('pages.quote.detail.notFound')}
        extra={
          <Link to="/quotes">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              {t('pages.quote.detail.backToList')}
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status as QuoteStatus
  const canSubmit = status === 'DRAFT' || status === 'REJECTED'
  // 审批按权限码而不是角色名：QuoteController 的 approve / reject 标的是 quote:approve，
  // 改造前写 `role === 'ADMIN'` —— 081 新增的角色里凡是拿到 quote:approve 的都批不了报价单。
  const canApprove = hasPerm(PERMS.quoteApprove, user) && status === 'PENDING_APPROVAL'

  const itemColumns = [
    { title: t('pages.quote.detail.colProduct'), dataIndex: 'productName' },
    { title: t('pages.quote.detail.colQty'), dataIndex: 'quantity' },
    {
      title: t('pages.quote.detail.colUnitPrice'),
      dataIndex: 'unitPrice',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
    {
      title: t('pages.quote.detail.colDiscount'),
      dataIndex: 'discount',
      render: (v: number) => `${Math.round(v * 100)}%`,
    },
    {
      title: t('pages.quote.detail.colSubtotal'),
      dataIndex: 'lineTotal',
      render: (v: number) => (v / 100).toLocaleString('zh-CN'),
    },
  ]

  return (
    <div>
      <Link to="/quotes" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.quote.detail.backToList')}
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.quoteNo}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {t('pages.quote.detail.createdAtPrefix')}：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
          </Typography.Text>
        </div>
        <Space>
          {canSubmit && (
            <Button
              type="primary"
              onClick={() => onAction(() => submitQuote(quoteId), t('pages.quote.detail.msgSubmitted'))}
            >
              {t('pages.quote.detail.submit')}
            </Button>
          )}
          {canApprove && (
            <>
              <Button
                type="primary"
                onClick={() => onAction(() => approveQuote(quoteId), t('pages.quote.detail.msgApproved'))}
              >
                {t('pages.quote.detail.approve')}
              </Button>
              <Button danger onClick={() => { rejectForm.resetFields(); setRejectOpen(true) }}>
                {t('pages.quote.detail.reject')}
              </Button>
            </>
          )}
          {/* 「导出报价单 PDF」**刻意不加权限判据**：该端点（`QuoteController.java:128-131`）挂的是
              **读码** `quote:read`，而能渲染出本页的人必然已持有该码（否则连 `GET /quotes/{id}` 都过不去），
              挂上去是一个恒真的空动作——既收不窄任何人，又会让后来者以为这里已经收过口。 */}
          <Button icon={<DownloadOutlined />} onClick={() => void onAction(() => exportQuotePdf(quoteId), t('pages.quote.detail.msgPdf'))}>
            {t('pages.quote.detail.exportPdf')}
          </Button>
        </Space>
      </div>

      <Card title={t('pages.quote.detail.basicInfo')} style={{ marginBottom: 16 }}>
        <Descriptions column={{ xs: 1, sm: 2, md: 3 }} bordered size="small">
          <Descriptions.Item label={t('pages.quote.detail.labelStatus')}>
            <Tag color={QUOTE_STATUS_COLORS[status]}>{labelOf(t, ENUM_KEYS.quoteStatus, status)}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.quote.detail.labelCustomer')}>
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.quote.detail.labelValidUntil')}>{data.validUntil ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.quote.detail.labelTotal')}>
            <Typography.Text strong>¥ {(data.totalAmount / 100).toLocaleString('zh-CN')}</Typography.Text>
          </Descriptions.Item>
          {data.approvedAt && (
            <Descriptions.Item label={t('pages.quote.detail.labelApprovedAt')}>
              {data.approvedAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.rejectReason && (
            <Descriptions.Item label={t('pages.quote.detail.labelRejectReason')} span={3}>
              <Typography.Text type="danger">{data.rejectReason}</Typography.Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label={t('pages.quote.detail.labelRemark')} span={3}>
            {data.remark ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <SignSection
        businessType="QUOTE"
        businessId={quoteId}
        canSign={status === 'APPROVED'}
        signFn={signQuote}
        fetchFn={fetchQuoteSignature}
        onSigned={invalidate}
      />

      <Card
        title={t('pages.quote.detail.items')}
        styles={{ body: { padding: 0 } }}
      >
        <Table<QuoteItem>
          rowKey="id"
          size="middle"
          dataSource={data.items ?? []}
          columns={itemColumns as never}
          pagination={false}
          locale={{ emptyText: t('pages.quote.detail.emptyItems') }}
        />
      </Card>

      <Modal
        title={t('pages.quote.detail.modalReject')}
        open={rejectOpen}
        onOk={() => void onReject()}
        onCancel={() => setRejectOpen(false)}
        okText={t('pages.quote.detail.confirmReject')}
        destroyOnClose
        width={480}
      >
        <Form form={rejectForm} name="rejectForm" layout="vertical">
          <Form.Item
            name="reason"
            label={t('pages.quote.detail.reason')}
            rules={[{ required: true, message: t('pages.quote.detail.msgReasonRequired') }]}
          >
            <Input.TextArea rows={3} placeholder={t('pages.quote.detail.phReject')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
