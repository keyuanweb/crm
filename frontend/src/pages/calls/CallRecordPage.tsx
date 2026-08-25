import { useRef, useState } from 'react'
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
import {
  CALL_DIRECTION_LABELS,
  CALL_RESULT_LABELS,
  type CallRecord,
  type CallStats,
} from '../../types/callRecord'

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
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<CallRecord | null>(null)
  const [stats, setStats] = useState<CallStats | null>(null)
  const [form] = Form.useForm<FormValues>()

  const refreshStats = async () => {
    try {
      setStats(await fetchCallStats({}))
    } catch {
      // 统计失败不阻塞
    }
  }

  const columns: ProColumns<CallRecord>[] = [
    { title: '客户', dataIndex: 'customerName', ellipsis: true },
    { title: '联系人', dataIndex: 'contactName', search: false },
    {
      title: '方向',
      dataIndex: 'direction',
      valueEnum: Object.fromEntries(Object.entries(CALL_DIRECTION_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => (
        <Tag color={row.direction === 'OUTBOUND' ? 'blue' : 'green'}>
          {CALL_DIRECTION_LABELS[row.direction] ?? row.direction}
        </Tag>
      ),
    },
    {
      title: '时长',
      dataIndex: 'durationSeconds',
      search: false,
      render: (_, row) => formatDuration(row.durationSeconds),
    },
    {
      title: '结果',
      dataIndex: 'result',
      valueEnum: Object.fromEntries(Object.entries(CALL_RESULT_LABELS).map(([k, v]) => [k, { text: v }])),
      render: (_, row) => <Tag>{CALL_RESULT_LABELS[row.result] ?? row.result}</Tag>,
    },
    {
      title: '时间',
      dataIndex: 'recordedAt',
      search: false,
      render: (_, row) => (row.recordedAt ? row.recordedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>编辑</a>,
        <Popconfirm key="del" title="删除该通话记录？" onConfirm={() => void onDelete(row)}>
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
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
        message.success('已保存')
      } else {
        await createCallRecord(values)
        message.success('已录入')
      }
      setModalOpen(false)
      actionRef.current?.reload()
      void refreshStats()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    }
  }

  const onDelete = async (row: CallRecord) => {
    try {
      await deleteCallRecord(row.id)
      message.success('已删除')
      actionRef.current?.reload()
      void refreshStats()
    } catch (err) {
      message.error(extractErrorMessage(err, '删除失败'))
    }
  }

  return (
    <>
      <Row gutter={12} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="通话次数" value={stats?.totalCount ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="总时长" value={formatDuration(stats?.totalDurationSeconds ?? 0)} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="平均时长" value={formatDuration(stats?.avgDurationSeconds ?? 0)} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="呼入/呼出"
              value={stats?.byDirection?.map((d) => `${CALL_DIRECTION_LABELS[d.direction as keyof typeof CALL_DIRECTION_LABELS] ?? d.direction} ${d.count}`).join(' / ') ?? '-'}
            />
          </Card>
        </Col>
      </Row>
      <ProTable<CallRecord>
        headerTitle="通话记录"
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
            录入通话
          </Button>,
        ]}
      />
      <Modal
        title={editing ? '编辑通话记录' : '录入通话记录'}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
            <CustomerSelect />
          </Form.Item>
          <Form.Item name="contactId" label="联系人">
            <Input placeholder="联系人 id（可空）" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
                <Select options={Object.entries(CALL_DIRECTION_LABELS).map(([value, label]) => ({ value, label }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="result" label="结果" rules={[{ required: true }]}>
                <Select options={Object.entries(CALL_RESULT_LABELS).map(([value, label]) => ({ value, label }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="durationSeconds" label="时长（秒）" rules={[{ required: true, message: '请输入时长' }]}>
            <InputNumber min={0} max={86400} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} placeholder="备注或录音链接" />
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
      placeholder="选择客户"
    />
  )
}
