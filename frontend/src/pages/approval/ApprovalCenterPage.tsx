import { useEffect, useRef, useState } from 'react'
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
      message.warning('驳回需填写意见')
      return
    }
    setSaving(true)
    try {
      const instanceId = actionTask.instanceId
      if (actionType === 'approve') {
        await approveTask(instanceId, actionTask.id, values.comment)
        message.success('已通过')
      } else if (actionType === 'reject') {
        await rejectTask(instanceId, actionTask.id, values.comment ?? '')
        message.success('已驳回')
      } else {
        await transferTask(instanceId, actionTask.id, values.toUserId!)
        message.success('已转交')
      }
      setActionTask(null)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '操作失败'))
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
      title: '审批事项',
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
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) => {
        const s = STATUS_LABELS[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={s.color}>{s.text}</Tag>
      },
    },
    { title: '意见', dataIndex: 'comment', search: false, ellipsis: true, render: (_, row) => row.comment || '-' },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 140,
      search: false,
      render: (_, row) => (row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, row) => (
        <Space size="small">
          {row.status === 'PENDING' ? (
            <>
              <a onClick={() => void openAction(row, 'approve')}>通过</a>
              <a style={{ color: '#ff4d4f' }} onClick={() => void openAction(row, 'reject')}>
                驳回
              </a>
              <a onClick={() => void openAction(row, 'transfer')}>转交</a>
            </>
          ) : null}
          <a onClick={() => void openDetail(row)}>详情</a>
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
              我的待办
            </Button>
            <Button size="small" type={tab === 'done' ? 'primary' : 'default'} onClick={() => setTab('done')}>
              我的已办
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
          actionType === 'approve' ? '审批通过' : actionType === 'reject' ? '审批驳回' : '转交审批'
        }
        open={!!actionTask}
        onOk={() => void submitAction()}
        confirmLoading={saving}
        onCancel={() => setActionTask(null)}
        okText="确定"
        destroyOnClose
      >
        <Form form={form} name="actionForm" layout="vertical">
          {actionType === 'transfer' ? (
            <Form.Item name="toUserId" label="转交给" rules={[{ required: true, message: '请选择用户' }]}>
              <Select style={{ width: '100%' }} placeholder="选择用户" options={userOptions} />
            </Form.Item>
          ) : (
            <Form.Item name="comment" label={actionType === 'reject' ? '驳回意见（必填）' : '意见（可选）'}>
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
              <Typography.Text strong>审批记录</Typography.Text>
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
