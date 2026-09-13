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
  Typography,
  Upload,
} from 'antd'
import {
  ArrowLeftOutlined,
  DownloadOutlined,
  InboxOutlined,
  ReloadOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
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
import { hasPerm } from '../../hooks/usePermission'
import { PERMS } from '../../constants/permissions'
import type { ContractStatus, ContractAttachment } from '../../types/contract'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { StatusTag, StatCard } from '../../components/ui'

export default function ContractDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const contractId = Number(id)
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
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
  // 审批按权限码而不是角色名：ContractController 的 approve / reject 标的是 contract:approve，
  // 改造前写 `role === 'ADMIN'` —— 081 新增的角色里凡是拿到 contract:approve 的都批不了合同，
  // 而这类角色在角色页上是被允许勾选该码的。
  const canApprove = hasPerm(PERMS.contractApprove, user) && status === 'PENDING_APPROVAL'
  const canEffective = status === 'APPROVED' || status === 'SIGNED'
  const canFinish = status === 'APPROVED' || status === 'SIGNED' || status === 'EFFECTIVE'
  // 终止合同 / 删除附件：PUT /contracts/{id}/terminate（ContractController.java:138-144）与
  // DELETE /contracts/{id}/attachments/{aid}（ContractAttachmentController.java:82-86）挂的都是
  // contract:update——与上面的 contractApprove **两码独立、不可互替**（FR-B07）。
  // 此处只加码闸门，审批判据不动；与状态判据（canFinish）是 ∧ 关系。
  const canUpdate = hasPerm(PERMS.contractUpdate, user)

  // 状态 Tag
  const renderStatus = (status?: string) => {
    if (!status) return <StatusTag>-</StatusTag>
    const type: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
      DRAFT: 'default',
      PENDING_APPROVAL: 'warning',
      REJECTED: 'danger',
      APPROVED: 'info',
      SIGNED: 'info',
      EFFECTIVE: 'success',
      COMPLETED: 'info',
      TERMINATED: 'danger',
    }
    const label = labelOf(t, ENUM_KEYS.contractStatus, status)
    return <StatusTag type={type[status] ?? 'default'}>{label}</StatusTag>
  }

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
        canUpdate ? (
          <a
            key="delete"
            style={{ color: 'var(--color-danger)', marginLeft: 8 }}
            onClick={() => onAction(() => deleteContractAttachment(contractId, row.id), t('pages.contract.detail.msgDeleted'))}
          >
            {t('pages.contract.detail.delete')}
          </a>
        ) : null,
      ],
    },
  ]

  // 统计卡片数据
  const statsCards = [
    {
      value: `¥${(data.amount / 100).toLocaleString('zh-CN')}`,
      label: t('pages.contract.detail.labelAmount'),
      icon: <FileTextOutlined />,
    },
    {
      value: data.startDate ?? '-',
      label: t('pages.contract.detail.labelEffectiveDate'),
      icon: <ClockCircleOutlined />,
    },
    {
      value: data.endDate ?? '-',
      label: t('pages.contract.detail.labelEndDate'),
      icon: <ClockCircleOutlined />,
    },
    {
      value: data.approvedAt ? new Date(data.approvedAt).toLocaleDateString('zh-CN') : '-',
      label: t('pages.contract.detail.labelApprovedAt'),
      icon: <CheckCircleOutlined />,
    },
  ]

  return (
    <div>
      {/* 返回按钮 */}
      <Link to="/contracts" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0 }}>
          {t('pages.contract.detail.backToList')}
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
            {/* 合同图标 */}
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
              📄
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4, flexWrap: 'wrap' }}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {data.contractNo} - {data.title}
                </Typography.Title>
                <StatusTag type={status === 'EFFECTIVE' ? 'success' : status === 'PENDING_APPROVAL' ? 'warning' : status === 'REJECTED' || status === 'TERMINATED' ? 'danger' : 'info'}>
                  {labelOf(t, ENUM_KEYS.contractStatus, status)}
                </StatusTag>
              </div>
              <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block' }}>
                {t('pages.contract.detail.labelCreatedAt')}：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
              </Typography.Text>
            </div>
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
                {canUpdate && (
                  <Button danger onClick={() => { terminateForm.resetFields(); setTerminateOpen(true) }}>
                    {t('pages.contract.detail.terminate')}
                  </Button>
                )}
              </>
            )}
            <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
              {t('pages.contract.detail.refresh')}
            </Button>
          </Space>
        </div>
      </Card>

      {/* 统计卡片行 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 20 }}>
        {statsCards.map((card, index) => (
          <StatCard key={index} value={card.value} label={card.label} icon={card.icon} />
        ))}
      </div>

      {/* 基本信息 */}
      <Card
        title={t('pages.contract.detail.basicInfo')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}
      >
        <Descriptions
          column={{ xs: 1, sm: 2, md: 3 }}
          bordered
          size="small"
          layout="horizontal"
        >
          <Descriptions.Item label={t('pages.contract.detail.labelStatus')}>
            {renderStatus(status)}
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelCustomer')}>
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.contract.detail.labelQuote')}>{data.quoteId ?? '-'}</Descriptions.Item>
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
            <Descriptions.Item label={t('pages.contract.detail.labelRejectReason')} span={3}>
              <Typography.Text type="danger">{data.rejectReason}</Typography.Text>
            </Descriptions.Item>
          )}
          {data.terminatedReason && (
            <Descriptions.Item label={t('pages.contract.detail.labelTerminateReason')} span={3}>
              <Typography.Text type="danger">{data.terminatedReason}</Typography.Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label={t('pages.contract.detail.labelRemark')} span={3}>
            {data.remark ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 签署区域 */}
      <SignSection
        businessType="CONTRACT"
        businessId={contractId}
        canSign={status === 'APPROVED'}
        signFn={signContract}
        fetchFn={fetchContractSignature}
        onSigned={invalidate}
      />

      {/* 合同正文 */}
      {data.content && (
        <Card
          title={t('pages.contract.detail.body')}
          bordered={false}
          style={{ borderRadius: 'var(--radius-lg)', marginBottom: 20 }}
        >
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.content}</Typography.Paragraph>
        </Card>
      )}

      {/* 附件 */}
      <Card
        title={t('pages.contract.detail.attachments')}
        bordered={false}
        style={{ borderRadius: 'var(--radius-lg)' }}
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
          bordered
        />
      </Card>

      {/* 拒绝弹窗 */}
      <Modal
        title={t('pages.contract.detail.modalReject')}
        open={rejectOpen}
        onOk={() => void onReject()}
        onCancel={() => setRejectOpen(false)}
        okText={t('pages.contract.detail.confirmReject')}
        destroyOnClose
        styles={{ body: { padding: '20px 24px' } }}
      >
        <Form form={rejectForm} name="contractRejectForm" layout="vertical">
          <Form.Item name="reason" label={t('pages.contract.detail.reason')} rules={[{ required: true, message: t('pages.contract.detail.msgReasonRequired') }]}>
            <Input.TextArea rows={3} placeholder={t('pages.contract.detail.phReject')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 终止弹窗 */}
      <Modal
        title={t('pages.contract.detail.modalTerminate')}
        open={terminateOpen}
        onOk={() => void onTerminate()}
        onCancel={() => setTerminateOpen(false)}
        okText={t('pages.contract.detail.confirmTerminate')}
        destroyOnClose
        styles={{ body: { padding: '20px 24px' } }}
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
