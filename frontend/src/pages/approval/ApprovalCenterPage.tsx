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
import { useAuthStore } from '../../store/authStore'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import type { ApprovalDetail, ApprovalTask } from '../../types/approval'

/**
 * 审批状态 Tag 颜色。文案走 i18n，这里只留颜色。
 *
 * <p>实例与任务的状态码几乎同名，颜色可以共用一份（同一个码在两边就是同一个语义）；
 * 但**文案不能共用一份映射**——`TRANSFERRED` 只出现在任务上、`CANCELED` 只出现在实例上，
 * 见 `ENUM_KEYS.approvalInstanceStatus` / `approvalTaskStatus` 的说明。
 */
const STATUS_COLORS: Record<string, string> = {
  PENDING: 'processing',
  APPROVED: 'green',
  REJECTED: 'red',
  // 重提审批时旧实例被作废（`ApprovalEngineService` L231）
  CANCELED: 'default',
  // 转交只发生在任务上
  TRANSFERRED: 'default',
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
  const user = useAuthStore((s) => s.user)

  /**
   * 三个操作链接（通过/驳回/转交）能否渲染：业务状态 ∧ **任务归属**。
   *
   * <p>逐字镜像后端 `ApprovalEngineService.checkApprover`（`approverId` 为 null ⇒ FORBIDDEN；
   * `approverId != 当前用户` ⇒ FORBIDDEN，**对 ADMIN 也不通融**）与 `requirePendingTask`
   * （非 `PENDING` ⇒ BAD_REQUEST）。**不引入权限码**：`approval:approve` 是死码，且挂码等于
   * 「有码者可审**任何**任务」，比归属判定**松**——那是削弱而非收窄。
   *
   * <p><b>为什么今天才把归属写进渲染条件</b>：两个列表端点（`/approvals/todos`、`/approvals/done`）
   * 本身就按 `approverId` 过滤，所以「只判 status」目前**恰好**等价于「是我的任务」——
   * 判据是靠**取数**隐式成立的，页面从没把它表达出来。列表端点一改、或这一列被复用到别处，
   * 就会露出恒 403 的按钮。写出这条判据 = 让渲染规则与真正决定放行的规则**同源**。
   */
  const canAct = (row: ApprovalTask) =>
    row.status === 'PENDING' && row.approverId != null && row.approverId === user?.id

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
      // 这一列是**审批任务**（行类型 `ApprovalTask`），走的必须是 approvalTaskStatus。
      render: (_, row) => (
        <Tag color={STATUS_COLORS[row.status] ?? 'default'}>
          {labelOf(t, ENUM_KEYS.approvalTaskStatus, row.status)}
        </Tag>
      ),
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
          {canAct(row) ? (
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
        width={480}
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
        title={detail ? `审批详情 #${detail.instance.id}` : t('pages.approval.detailTitle')}
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
              <Tag color={STATUS_COLORS[detail.instance.status] ?? 'default'}>
                {labelOf(t, ENUM_KEYS.approvalInstanceStatus, detail.instance.status)}
              </Tag>
            </div>

            <div style={{ padding: 12, background: '#fafafa', borderRadius: 8 }}>
              <Steps
                size="small"
                direction="vertical"
                current={detail.tasks.filter((task) => task.status !== 'PENDING').length}
                items={detail.tasks.map((task) => ({
                  title: task.nodeName,
                  // 注意：回调形参必须叫 task 而不是 t——叫 t 会遮蔽 useTranslation 的 t，
                  // labelOf 就拿不到翻译函数了（同 TaskCalendarPage 的坑）。
                  // 这里的每一步是一个「任务」，故用 approvalTaskStatus（含 TRANSFERRED）。
                  description: `${labelOf(t, ENUM_KEYS.approvalTaskStatus, task.status)}${task.comment ? '：' + task.comment : ''}`,
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
                      {labelOf(t, ENUM_KEYS.approvalAction, l.action)}
                      {t('pages.approval.logOperatorPrefix')}
                      {l.operator}
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
