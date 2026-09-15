import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Card, Col, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Statistic, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createCallRecord,
  deleteCallRecord,
  fetchCallRecords,
  fetchCallStats,
  updateCallRecord,
} from '../../services/callRecordService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { CallRecord, CallStats } from '../../types/callRecord'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

interface FormValues {
  customerId?: number
  contactId?: number
  direction: string
  durationSeconds: number
  result: string
  remark?: string
}

/** 通话记录页（061，ADMIN+SALES+SUPPORT）。 */
export default function CallRecordPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CallRecord | null>(null)
  const [stats, setStats] = useState<CallStats | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 删除通话记录走 DELETE /call-records/{id}，CallRecordController 上标的是 call_record:delete。
  const can = usePerms([PERMS.callRecordDelete])

  const CALL_DIRECTION_LABELS: Record<string, string> = {
    INBOUND: t('pages.call.directionInbound'),
    OUTBOUND: t('pages.call.directionOutbound'),
  }
  const CALL_RESULT_LABELS: Record<string, string> = {
    CONNECTED: t('pages.call.resultConnected'),
    NO_ANSWER: t('pages.call.resultNoAnswer'),
    BUSY: t('pages.call.resultBusy'),
    FAILED: t('pages.call.resultFailed'),
  }

  const refreshStats = async () => {
    try {
      setStats(await fetchCallStats({}))
    } catch {
      // 统计失败不阻塞
    }
  }

  const columns: ProColumns<CallRecord>[] = [
    { title: t('pages.call.colCustomer'), dataIndex: 'customerName', ellipsis: true },
    { title: t('pages.call.colContact'), dataIndex: 'contactName', search: false },
    {
      title: t('pages.call.colDirection'),
      dataIndex: 'direction',
      valueEnum: Object.fromEntries(Object.entries(CALL_DIRECTION_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => (
        <Tag color={row.direction === 'OUTBOUND' ? 'blue' : 'green'}>
          {CALL_DIRECTION_LABELS[row.direction] ?? row.direction}
        </Tag>
      ),
    },
    {
      title: t('pages.call.colDuration'),
      dataIndex: 'durationSeconds',
      search: false,
      render: (_, row) => formatDuration(row.durationSeconds),
    },
    {
      title: t('pages.call.colResult'),
      dataIndex: 'result',
      valueEnum: Object.fromEntries(Object.entries(CALL_RESULT_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag>{CALL_RESULT_LABELS[row.result] ?? row.result}</Tag>,
    },
    {
      title: t('pages.call.colTime'),
      dataIndex: 'recordedAt',
      search: false,
      render: (_, row) => (row.recordedAt ? row.recordedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: t('pages.call.colAction'),
      valueType: 'option',
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>{t('common.button.edit')}</a>,
        can[PERMS.callRecordDelete] ? (
          <Popconfirm key="del" title={t('pages.call.confirmDelete')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('common.button.delete')}</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  const openEdit = (row: CallRecord) => {
    setEditing(row)
    form.setFieldsValue({
      customerId: row.customerId,
      contactId: row.contactId,
      direction: row.direction,
      durationSeconds: row.durationSeconds,
      result: row.result,
      remark: row.remark,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateCallRecord(editing.id, values)
        message.success(t('common.message.saved'))
      } else {
        await createCallRecord(values)
        message.success(t('pages.call.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
      void refreshStats()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.call.msgSaveFailed')))
    }
  }

  const onDelete = async (row: CallRecord) => {
    try {
      await deleteCallRecord(row.id)
      message.success(t('pages.call.msgDeleted'))
      actionRef.current?.reload()
      void refreshStats()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.call.msgDeleteFailed')))
    }
  }

  return (
    <>
      <Row gutter={12}>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('pages.call.statTotal')} value={stats?.totalCount ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('pages.call.statTotalDuration')} value={formatDuration(stats?.totalDurationSeconds ?? 0)} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title={t('pages.call.statAvgDuration')} value={formatDuration(stats?.avgDurationSeconds ?? 0)} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={t('pages.call.statByDirection')}
              value={stats?.byDirection?.map((d) => `${CALL_DIRECTION_LABELS[d.direction as keyof typeof CALL_DIRECTION_LABELS] ?? d.direction} ${d.count}`).join(' / ') ?? '-'}
            />
          </Card>
        </Col>
      </Row>
      <ProTable<CallRecord>
        headerTitle={t('pages.call.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchCallRecords({
            keyword: params.customerName,
            direction: params.direction,
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
          })
          void refreshStats()
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              form.resetFields()
              form.setFieldsValue({ direction: 'OUTBOUND', durationSeconds: 0, result: 'CONNECTED' })
              setModalOpen(true)
            }}
          >
            {t('pages.call.create')}
          </Button>,
        ]}
      />
      <Modal
        title={editing ? t('pages.call.editModal') : t('pages.call.createModal')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="customerId" label={t('pages.call.colCustomer')} rules={[{ required: true, message: t('pages.call.msgCustomerRequired') }]}>
              <CustomerSelect />
            </Form.Item>
            <Form.Item name="contactId" label={t('pages.call.colContact')}>
              <Input placeholder={t('pages.call.placeholderContact')} />
            </Form.Item>
            <Form.Item name="direction" label={t('pages.call.colDirection')} rules={[{ required: true }]}>
              <Select options={Object.entries(CALL_DIRECTION_LABELS).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="result" label={t('pages.call.colResult')} rules={[{ required: true }]}>
              <Select options={Object.entries(CALL_RESULT_LABELS).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="durationSeconds" label={t('pages.call.formDuration')} rules={[{ required: true, message: t('pages.call.msgDurationRequired') }]}>
              <InputNumber min={0} max={86400} style={{ width: '100%' }} />
            </Form.Item>
          </FormGrid>
          <Form.Item name="remark" label={t('pages.call.formRemark')}>
            <Input.TextArea rows={2} maxLength={500} placeholder={t('pages.call.placeholderRemark')} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function formatDuration(seconds: number) {
  if (!seconds) return '0s'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}h${m}m`
  if (m > 0) return `${m}m${s}s`
  return `${s}s`
}

function CustomerSelect() {
  const { t } = useTranslation()
  const [options, setOptions] = useState<{ value: number; label: string }[]>([])
  const load = async (keyword?: string) => {
    try {
      const res = await fetchCustomers({ keyword, page: 1, pageSize: 50 })
      setOptions(res.items.map((c) => ({ value: c.id, label: `${c.name}（${c.company ?? '-'}）` })))
    } catch {
      setOptions([])
    }
  }
  return (
    <Select
      showSearch
      filterOption={false}
      onSearch={(v) => void load(v)}
      onFocus={() => void load()}
      options={options}
      placeholder={t('pages.call.placeholderCustomer')}
    />
  )
}
