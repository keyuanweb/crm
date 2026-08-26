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
  Upload,
} from 'antd'
import {
  ArrowLeftOutlined,
  DownloadOutlined,
  InboxOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveContract,
  completeContract,
  deleteContractAttachment,
  downloadContractAttachment,
  effectiveContract,
  fetchContract,
  rejectContract,
  submitContract,
  terminateContract,
  uploadContractAttachment,
} from '../../services/contractService'
import { extractErrorMessage } from '../../services/apiClient'
import { signContract, fetchContractSignature } from '../../services/signatureService'
import SignSection from '../../components/SignSection'
import { useAuthStore } from '../../store/authStore'
import {
  CONTRACT_STATUS_COLORS,
  CONTRACT_STATUS_LABELS,
  type ContractStatus,
} from '../../types/contract'
import type { ContractAttachment } from '../../types/contract'

const { Paragraph } = Typography

export default function ContractDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const contractId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const [rejectOpen, setRejectOpen] = useState(false)
  const [terminateOpen, setTerminateOpen] = useState(false)
  const [rejectForm] = Form.useForm<{ reason: string }>()
  const [terminateForm] = Form.useForm<{ reason: string }>()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['contract', contractId],
    queryFn: () => fetchContract(contractId),
    enabled: Number.isFinite(contractId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['contract', contractId] })
    void refetch()
  }

  const onAction = async (fn: () => Promise<unknown>, successMsg: string) => {
    try {
      await fn()
      message.success(successMsg)
      invalidate()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.contract.detail.msgFailed')))
    }
  }

  const onReject = async () => {
    const values = await rejectForm.validateFields()
    await onAction(() => rejectContract(contractId, values.reason), t('pages.contract.detail.msgRejected'))
    setRejectOpen(false)
  }

  const onTerminate = async () => {
    const values = await terminateForm.validateFields()
    await onAction(() => terminateContract(contractId, values.reason), t('pages.contract.detail.msgTerminated'))
    setTerminateOpen(false)
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title={t('pages.contract.detail.notFound')}
        extra={
          <Link to="/contracts">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              {t('pages.contract.detail.backToList')}
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status as ContractStatus
  const canSubmit = status === 'DRAFT' || status === 'REJECTED'
  const canApprove = isAdmin && status === 'PENDING_APPROVAL'
  const canEffective = status === 'APPROVED' || status === 'SIGNED'
  const canFinish = status === 'APPROVED' || status === 'SIGNED' || status === 'EFFECTIVE'

  const attachmentColumns = [
    {
      title: t('pages.contract.detail.colFileName'),
      dataIndex: 'fileName',
    },
    {
      title: t('pages.contract.detail.colSize'),
      dataIndex: 'fileSize',
      render: (v: number) => (v / 1024).toFixed(1) + ' KB',
    },
    {
      title: t('pages.contract.detail.colUploaded'),
      dataIndex: 'createdAt',
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.contract.detail.colAction'),
      key: 'actions',
      width: 140,
      render: (_: unknown, row: ContractAttachment) => [
        <a
          key="download"
          onClick={() => void onAction(() => downloadContractAttachment(contractId, row.id), t('pages.contract.detail.msgDownloading'))}
        >
          <DownloadOutlined /> {t('pages.contract.detail.download')}
        </a>,
        <a
          key="delete"
          style={{ color: '#ff4d4f', marginLeft: 8 }}
          onClick={() => onAction(() => deleteContractAttachment(contractId, row.id), t('pages.contract.detail.msgDeleted'))}
        >
          {t('pages.contract.detail.delete')}
        </a>,
      ],
    },
  ]

  return (
    <div>
      <Link to="/contracts" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          {t('pages.contract.detail.backToList')}
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.contractNo} - {data.title}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {t('pages.contract.detail.labelCreatedAt')}：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
          </Typography.Text>
        </div>
        <Space>
          {canSubmit && (
            <Button type="primary" onClick={() => onAction(() => submitContract(contractId), t('pages.contract.detail.msgSubmitted'))}>
              {t('pages.contract.detail.submit')}
            </Button>
          )}
          {canApprove && (
            <>
              <Button type="primary" onClick={() => onAction(() => approveContract(contractId), t('pages.contract.detail.msgApproved'))}>
                {t('pages.contract.detail.approve')}
              </Button>
              <Button danger onClick={() => { rejectForm.resetFields(); setRejectOpen(true) }}>
                {t('pages.contract.detail.reject')}
              </Button>
            </>
          )}
          {canEffective && (
            <Button onClick={() => onAction(() => effectiveContract(contractId), t('pages.contract.detail.msgEffective'))}>
              {t('pages.contract.detail.markEffective')}
            </Button>
          )}
          {canFinish && (
            <>
              <Button onClick={() => onAction(() => completeContract(contractId), t('pages.contract.detail.msgCompleted'))}>
                {t('pages.contract.detail.markComplete')}
              </Button>
              <Button danger onClick={() => { terminateForm.resetFields(); setTerminateOpen(true) }}>
                {t('pages.contract.detail.terminate')}
              </Button>
            </>
          )}
          <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
            {t('pages.contract.detail.refresh')}
          </Button>
        </Space>
      </div>

      <Card title={t('pages.contract.detail.basicInfo')} style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('pages.contract.detail.labelStatus')}>
            <Tag color={CONTRACT_STATUS_COLORS[status]}>{CONTRACT_STATUS_LABELS[status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelCustomer')}>
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelQuote')}>{data.quoteId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelAmount')}>
            <Typography.Text strong>¥ {(data.amount / 100).toLocaleString('zh-CN')}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelEffectiveDate')}>{data.startDate ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelEndDate')}>{data.endDate ?? '-'}</Descriptions.Item>
          {data.approvedAt && (
            <Descriptions.Item label={t('pages.contract.detail.labelApprovedAt')}>
              {data.approvedAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.effectiveAt && (
            <Descriptions.Item label={t('pages.contract.detail.labelEffectiveAt')}>
              {data.effectiveAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.rejectReason && (
            <Descriptions.Item label={t('pages.contract.detail.labelRejectReason')} span={2}>
              <Typography.Text type="danger">{data.rejectReason}</Typography.Text>
            </Descriptions.Item>
          )}
          {data.terminatedReason && (
            <Descriptions.Item label={t('pages.contract.detail.labelTerminateReason')} span={2}>
              <Typography.Text type="danger">{data.terminatedReason}</Typography.Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label={t('pages.contract.detail.labelRemark')} span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <SignSection
        businessType="CONTRACT"
        businessId={contractId}
        canSign={status === 'APPROVED'}
        signFn={signContract}
        fetchFn={fetchContractSignature}
        onSigned={invalidate}
      />

      {data.content && (
        <Card title={t('pages.contract.detail.body')} style={{ marginBottom: 16, borderRadius: 10 }}>
          <Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.content}</Paragraph>
        </Card>
      )}

      <Card
        title={t('pages.contract.detail.attachments')}
        style={{ marginBottom: 16, borderRadius: 10 }}
        extra={
          <Upload
            accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx"
            showUploadList={false}
            beforeUpload={(file) => {
              void onAction(() => uploadContractAttachment(contractId, file), t('pages.contract.detail.msgUploaded')).then(() => {})
              return false
            }}
          >
            <Button icon={<InboxOutlined />}>{t('pages.contract.detail.uploadAttachment')}</Button>
          </Upload>
        }
      >
        <Table<ContractAttachment>
          rowKey="id"
          size="small"
          dataSource={data.attachments ?? []}
          columns={attachmentColumns as never}
          pagination={false}
          locale={{ emptyText: t('pages.contract.detail.emptyAttachments') }}
        />
      </Card>

      <Modal
        title={t('pages.contract.detail.modalReject')}
        open={rejectOpen}
        onOk={() => void onReject()}
        onCancel={() => setRejectOpen(false)}
        okText={t('pages.contract.detail.confirmReject')}
        destroyOnClose
      >
        <Form form={rejectForm} name="contractRejectForm" layout="vertical">
          <Form.Item name="reason" label={t('pages.contract.detail.reason')} rules={[{ required: true, message: t('pages.contract.detail.msgReasonRequired') }]}>
            <Input.TextArea rows={3} placeholder={t('pages.contract.detail.phReject')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('pages.contract.detail.modalTerminate')}
        open={terminateOpen}
        onOk={() => void onTerminate()}
        onCancel={() => setTerminateOpen(false)}
        okText={t('pages.contract.detail.confirmTerminate')}
        destroyOnClose
      >
        <Form form={terminateForm} name="contractTerminateForm" layout="vertical">
          <Form.Item name="reason" label={t('pages.contract.detail.terminateReason')} rules={[{ required: true, message: t('pages.contract.detail.msgTerminateRequired') }]}>
            <Input.TextArea rows={3} placeholder={t('pages.contract.detail.phTerminate')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
