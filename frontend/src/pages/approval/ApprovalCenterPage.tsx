import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import {
  approveTask,
  fetchApprovalDetail,
  fetchApprovalDone,
  fetchApprovalTodos,
  rejectTask,
  transferTask,
} from '../../services/approvalService'
import { extractErrorMessage } from '../../services/apiClient'
import { fetchUsers } from '../../services/userService'
import type { ApprovalDetail, ApprovalTask } from '../../types/approval'

const STATUS_LABELS: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
  TRANSFERRED: { text: '已转交', color: 'default' },
}

const ACTION_LABELS: Record<string, string> = {
  SUBMIT: '提交审批',
  APPROVE: '通过',
  REJECT: '驳回',
  TRANSFER: '转交',
  RENEW: '重提',
}

export default function ApprovalCenterPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [tab, setTab] = useState<'todos' | 'done'>('todos')
  const [detail, setDetail] = useState<ApprovalDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [actionTask, setActionTask] = useState<ApprovalTask | null>(null)
  const [actionType, setActionType] = useState<'approve' | 'reject' | 'transfer'>('approve')
  const [saving, setSaving] = useState(false)
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [form] = Form.useForm<{ comment?: string; toUserId?: number }>()

  const loadUsers = async () => {
    try {
      const users = await fetchUsers({ page: 1, pageSize: 100 })
      setUserOptions(users.items.map((u) => ({ value: u.id, label: u.displayName || u.username })))
    } catch {
      setUserOptions([])
    }
  }

  const openAction = async (task: ApprovalTask, type: 'approve' | 'reject' | 'transfer') => {
    setActionTask(task)
    setActionType(type)
    form.resetFields()
    if (type === 'transfer') {
      await loadUsers()
    }
  }

  const submitAction = async () => {
    if (!actionTask) return
    const values = await form.validateFields()
    if (actionType === 'reject' && !values.comment?.trim()) {
      message.warning(t('pages.approval.list.msgRejectRequired'))
      return
    }
    setSaving(true)
    try {
      const instanceId = actionTask.instanceId
      if (actionType === 'approve') {
        await approveTask(instanceId, actionTask.id, values.comment)
        message.success(t('pages.approval.list.msgApproved'))
      } else if (actionType === 'reject') {
        await rejectTask(instanceId, actionTask.id, values.comment ?? '')
        message.success(t('pages.approval.list.msgRejected'))
      } else {
        await transferTask(instanceId, actionTask.id, values.toUserId!)
        message.success(t('pages.approval.list.msgTransferred'))
      }
      setActionTask(null)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('common.message.failed')))
    } finally {
      setSaving(false)
    }
  }

  const openDetail = async (task: ApprovalTask) => {
    const d = await fetchApprovalDetail(task.instanceId)
    setDetail(d)
    setDetailOpen(true)
  }

  useEffect(() => {
    actionRef.current?.reload()
  }, [tab])

  const columns: ProColumns<ApprovalTask>[] = [
    {
      title: t('pages.approval.list.colTitle'),
      dataIndex: 'instanceId',
      search: false,
      render: (_, row) => {
        return (
          <Typography.Text strong>
            #{row.instanceId} · {row.nodeName}
          </Typography.Text>
        )
      },
    },
    {
      title: t('pages.approval.list.colStatus'),
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) => {
        const s = STATUS_LABELS[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={s.color}>{s.text}</Tag>
      },
    },
    { title: t('pages.approval.list.colComment'), dataIndex: 'comment', search: false, ellipsis: true, render: (_, row) => row.comment || '-' },
    {
      title: t('pages.approval.list.colTime'),
      dataIndex: 'createdAt',
      width: 140,
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.approval.list.colAction'),
      valueType: 'option',
      width: 200,
      render: (_, row) => (
        <Space size="small">
          {row.status === 'PENDING' ? (
            <>
              <a onClick={() => void openAction(row, 'approve')}>{t('pages.approval.list.approve')}</a>
              <a style={{ color: '#ff4d4f' }} onClick={() => void openAction(row, 'reject')}>
                {t('pages.approval.list.reject')}
              </a>
              <a onClick={() => void openAction(row, 'transfer')}>{t('pages.approval.list.transfer')}</a>
            </>
          ) : null}
          <a onClick={() => void openDetail(row)}>{t('pages.approval.list.detail')}</a>
        </Space>
      ),
    },
  ]

  return (
    <>
      <ProTable<ApprovalTask>
        size="small"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        headerTitle={
          <Space>
            <Button
              type={tab === 'todos' ? 'primary' : 'default'}
              size="small"
              onClick={() => setTab('todos')}
            >
              {t('pages.approval.list.todos')}
            </Button>
            <Button size="small" type={tab === 'done' ? 'primary' : 'default'} onClick={() => setTab('done')}>
              {t('pages.approval.list.doneTab')}
            </Button>
          </Space>
        }
        request={async () => {
          const items = tab === 'todos' ? await fetchApprovalTodos() : await fetchApprovalDone()
          return { data: items, success: true, total: items.length }
        }}
      />

      {/* 操作弹窗 */}
      <Modal
        title={
          actionType === 'approve'
            ? t('pages.approval.list.modalApprove')
            : actionType === 'reject'
              ? t('pages.approval.list.modalReject')
              : t('pages.approval.list.modalTransfer')
        }
        open={!!actionTask}
        onOk={() => void submitAction()}
        confirmLoading={saving}
        onCancel={() => setActionTask(null)}
        okText={t('pages.approval.list.ok')}
        destroyOnClose
      >
        <Form form={form} name="actionForm" layout="vertical">
          {actionType === 'transfer' ? (
            <Form.Item name="toUserId" label={t('pages.approval.list.transferTo')} rules={[{ required: true, message: t('pages.approval.list.msgUserRequired') }]}>
              <Select style={{ width: '100%' }} placeholder={t('pages.approval.list.selectUser')} options={userOptions} />
            </Form.Item>
          ) : (
            <Form.Item name="comment" label={actionType === 'reject' ? t('pages.approval.list.commentRequired') : t('pages.approval.list.commentOptional')}>
              <Input.TextArea rows={3} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* 详情抽屉 */}
      <Drawer
        title={detail ? `审批详情 #${detail.instance.id}` : '审批详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={520}
      >
        {detail && (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <div>
              <Typography.Title level={5} style={{ marginBottom: 4 }}>
                {detail.instance.title}
              </Typography.Title>
              <Tag color={STATUS_LABELS[detail.instance.status]?.color ?? 'default'}>
                {STATUS_LABELS[detail.instance.status]?.text ?? detail.instance.status}
              </Tag>
            </div>

            <div style={{ padding: 12, background: '#fafafa', borderRadius: 8 }}>
              <Steps
                size="small"
                direction="vertical"
                current={detail.tasks.filter((t) => t.status !== 'PENDING').length}
                items={detail.tasks.map((t) => ({
                  title: t.nodeName,
                  description: `${STATUS_LABELS[t.status]?.text ?? t.status}${t.comment ? '：' + t.comment : ''}`,
                }))}
              />
            </div>

            <div>
              <Typography.Text strong>{t('pages.approval.list.recordsTitle')}</Typography.Text>
              <Timeline
                style={{ marginTop: 8 }}
                items={detail.logs.map((l) => ({
                  children: (
                    <span>
                      {ACTION_LABELS[l.action] ?? l.action} · 操作人 #{l.operator}
                      {l.comment ? `：「${l.comment}」` : ''}
                      <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                        {l.createdAt?.replace('T', ' ').slice(0, 16)}
                      </Typography.Text>
                    </span>
                  ),
                }))}
              />
            </div>
          </Space>
        )}
      </Drawer>
    </>
  )
}
