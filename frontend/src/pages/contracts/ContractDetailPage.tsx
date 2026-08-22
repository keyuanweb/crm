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
import { useAuthStore } from '../../store/authStore'
import {
  CONTRACT_STATUS_COLORS,
  CONTRACT_STATUS_LABELS,
  type ContractStatus,
} from '../../types/contract'
import type { ContractAttachment } from '../../types/contract'

const { Paragraph } = Typography

export default function ContractDetailPage() {
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
      message.error(extractErrorMessage(err, '操作失败'))
    }
  }

  const onReject = async () => {
    const values = await rejectForm.validateFields()
    await onAction(() => rejectContract(contractId, values.reason), '已拒绝')
    setRejectOpen(false)
  }

  const onTerminate = async () => {
    const values = await terminateForm.validateFields()
    await onAction(() => terminateContract(contractId, values.reason), '已终止')
    setTerminateOpen(false)
  }

  if (isLoading) {
    return <Card loading style={{ minHeight: 300 }} />
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title="合同不存在或已被删除"
        extra={
          <Link to="/contracts">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              返回合同列表
            </Button>
          </Link>
        }
      />
    )
  }

  const status = data.status as ContractStatus
  const canSubmit = status === 'DRAFT' || status === 'REJECTED'
  const canApprove = isAdmin && status === 'PENDING_APPROVAL'
  const canEffective = status === 'APPROVED'
  const canFinish = status === 'APPROVED' || status === 'EFFECTIVE'

  const attachmentColumns = [
    {
      title: '文件名',
      dataIndex: 'fileName',
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      render: (v: number) => (v / 1024).toFixed(1) + ' KB',
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, row: ContractAttachment) => [
        <a
          key="download"
          onClick={() => void onAction(() => downloadContractAttachment(contractId, row.id), '已开始下载')}
        >
          <DownloadOutlined /> 下载
        </a>,
        <a
          key="delete"
          style={{ color: '#ff4d4f', marginLeft: 8 }}
          onClick={() => onAction(() => deleteContractAttachment(contractId, row.id), '已删除')}
        >
          删除
        </a>,
      ],
    },
  ]

  return (
    <div>
      <Link to="/contracts" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回合同列表
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.contractNo} - {data.title}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            创建时间：{data.createdAt ? data.createdAt.replace('T', ' ').slice(0, 19) : '-'}
          </Typography.Text>
        </div>
        <Space>
          {canSubmit && (
            <Button type="primary" onClick={() => onAction(() => submitContract(contractId), '已提交审批')}>
              提交审批
            </Button>
          )}
          {canApprove && (
            <>
              <Button type="primary" onClick={() => onAction(() => approveContract(contractId), '审批已通过')}>
                审批通过
              </Button>
              <Button danger onClick={() => { rejectForm.resetFields(); setRejectOpen(true) }}>
                拒绝
              </Button>
            </>
          )}
          {canEffective && (
            <Button onClick={() => onAction(() => effectiveContract(contractId), '已标记生效')}>
              标记生效
            </Button>
          )}
          {canFinish && (
            <>
              <Button onClick={() => onAction(() => completeContract(contractId), '已标记完成')}>
                标记完成
              </Button>
              <Button danger onClick={() => { terminateForm.resetFields(); setTerminateOpen(true) }}>
                终止
              </Button>
            </>
          )}
          <Button icon={<ReloadOutlined />} onClick={() => void refetch()}>
            刷新
          </Button>
        </Space>
      </div>

      <Card title="基本信息" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="状态">
            <Tag color={CONTRACT_STATUS_COLORS[status]}>{CONTRACT_STATUS_LABELS[status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="客户">
            <Link to={`/customers/${data.customerId}`}>{data.customerName ?? '-'}</Link>
          </Descriptions.Item>
          <Descriptions.Item label="关联报价单">{data.quoteId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="金额（元）">
            <Typography.Text strong>¥ {(data.amount / 100).toLocaleString('zh-CN')}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="生效日期">{data.startDate ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="结束日期">{data.endDate ?? '-'}</Descriptions.Item>
          {data.approvedAt && (
            <Descriptions.Item label="审批时间">
              {data.approvedAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.effectiveAt && (
            <Descriptions.Item label="生效时间">
              {data.effectiveAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          )}
          {data.rejectReason && (
            <Descriptions.Item label="拒绝意见" span={2}>
              <Typography.Text type="danger">{data.rejectReason}</Typography.Text>
            </Descriptions.Item>
          )}
          {data.terminatedReason && (
            <Descriptions.Item label="终止原因" span={2}>
              <Typography.Text type="danger">{data.terminatedReason}</Typography.Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="备注" span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {data.content && (
        <Card title="合同正文" style={{ marginBottom: 16, borderRadius: 10 }}>
          <Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.content}</Paragraph>
        </Card>
      )}

      <Card
        title="附件"
        style={{ marginBottom: 16, borderRadius: 10 }}
        extra={
          <Upload
            accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx"
            showUploadList={false}
            beforeUpload={(file) => {
              void onAction(() => uploadContractAttachment(contractId, file), '上传成功').then(() => {})
              return false
            }}
          >
            <Button icon={<InboxOutlined />}>上传附件</Button>
          </Upload>
        }
      >
        <Table<ContractAttachment>
          rowKey="id"
          size="small"
          dataSource={data.attachments ?? []}
          columns={attachmentColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无附件' }}
        />
      </Card>

      <Modal
        title="拒绝合同"
        open={rejectOpen}
        onOk={() => void onReject()}
        onCancel={() => setRejectOpen(false)}
        okText="确认拒绝"
        destroyOnClose
      >
        <Form form={rejectForm} name="contractRejectForm" layout="vertical">
          <Form.Item name="reason" label="拒绝意见" rules={[{ required: true, message: '请填写拒绝意见' }]}>
            <Input.TextArea rows={3} placeholder="如：条款需修改" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="终止合同"
        open={terminateOpen}
        onOk={() => void onTerminate()}
        onCancel={() => setTerminateOpen(false)}
        okText="确认终止"
        destroyOnClose
      >
        <Form form={terminateForm} name="contractTerminateForm" layout="vertical">
          <Form.Item name="reason" label="终止原因" rules={[{ required: true, message: '请填写终止原因' }]}>
            <Input.TextArea rows={3} placeholder="如：客户违约" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
