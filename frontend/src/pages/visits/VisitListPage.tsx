import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  App,
  Button,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { EnvironmentOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  cancelVisit,
  checkInVisit,
  createVisit,
  fetchVisitStats,
  fetchVisits,
  getCurrentPosition,
  updateVisit,
} from '../../services/visitService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import type { FieldVisit, VisitPayload } from '../../types/visit'

const STATUS_META: Record<string, { text: string; color: string }> = {
  PLANNED: { text: '计划中', color: 'processing' },
  DONE: { text: '已完成', color: 'green' },
  CANCELED: { text: '已取消', color: 'default' },
}

export default function VisitListPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<FieldVisit | null>(null)
  const [checkInVisitRow, setCheckInVisitRow] = useState<FieldVisit | null>(null)
  const [checkInForm] = Form.useForm<{ summary?: string; locationText?: string }>()
  const [customerOptions, setCustomerOptions] = useState<{ label: string; value: number }[]>([])
  const [stats, setStats] = useState<{ totalPlanned: number; totalDone: number }>({ totalPlanned: 0, totalDone: 0 })
  const [form] = Form.useForm<VisitPayload>()

  const reload = () => actionRef.current?.reload()

  useEffect(() => {
    void fetchCustomers({ page: 1, pageSize: 100 }).then((r) =>
      setCustomerOptions(r.items.map((c) => ({ label: `${c.name}${c.company ? `（${c.company}）` : ''}`, value: c.id }))),
    )
    void loadStats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadStats = async () => {
    try {
      const s = await fetchVisitStats()
      setStats({ totalPlanned: s.totalPlanned, totalDone: s.totalDone })
    } catch {
      // 忽略
    }
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: FieldVisit) => {
    setEditing(row)
    form.setFieldsValue({
      customerId: row.customerId,
      theme: row.theme,
      visitTime: row.visitTime,
      durationMinutes: row.durationMinutes,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: VisitPayload = {
      customerId: values.customerId,
      theme: values.theme.trim(),
      visitTime: dayjs(values.visitTime).format('YYYY-MM-DDTHH:mm:ss'),
      durationMinutes: values.durationMinutes,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateVisit(editing.id, payload)
        message.success('已保存')
      } else {
        await createVisit(payload)
        message.success('已创建')
      }
      setModalOpen(false)
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const onCancel = async (row: FieldVisit) => {
    try {
      await cancelVisit(row.id)
      message.success('已取消')
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, '取消失败'))
    }
  }

  const openCheckIn = (row: FieldVisit) => {
    setCheckInVisitRow(row)
    checkInForm.resetFields()
  }

  const onCheckIn = async () => {
    if (!checkInVisitRow) return
    const values = await checkInForm.validateFields()
    let position: { latitude: number; longitude: number } | undefined
    try {
      position = await getCurrentPosition()
    } catch (err) {
      message.warning((err as Error).message)
    }
    setSaving(true)
    try {
      await checkInVisit(checkInVisitRow.id, {
        latitude: position?.latitude,
        longitude: position?.longitude,
        locationText: values.locationText || (position ? `${position.latitude.toFixed(6)}, ${position.longitude.toFixed(6)}` : undefined),
        summary: values.summary,
      })
      message.success('签到成功，小结已写入客户跟进')
      setCheckInVisitRow(null)
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, '签到失败'))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<FieldVisit>[] = [
    { title: '主题', dataIndex: 'theme' },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 160,
      render: (_, row) => <a href={`#/customers/${row.customerId}`}>{row.customerName}</a>,
    },
    {
      title: '计划时间',
      dataIndex: 'visitTime',
      width: 150,
      render: (_, row) => (row.visitTime ? row.visitTime.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, row) => {
        const m = STATUS_META[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={m.color}>{m.text}</Tag>
      },
    },
    {
      title: '签到',
      search: false,
      width: 180,
      render: (_, row) =>
        row.checkInTime ? (
          <Space size={4} direction="vertical" style={{ fontSize: 12 }}>
            <span>{row.checkInTime.replace('T', ' ').slice(0, 16)}</span>
            {row.lateFlag ? <Tag color="orange" style={{ fontSize: 11 }}>补签</Tag> : null}
          </Space>
        ) : (
          <span style={{ color: '#bfbfbf' }}>-</span>
        ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 170,
      render: (_, row) => [
        row.status === 'PLANNED' ? (
          <a key="checkin" onClick={() => openCheckIn(row)}>
            <EnvironmentOutlined /> 签到
          </a>
        ) : null,
        row.status === 'PLANNED' ? <a key="edit" onClick={() => openEdit(row)}>编辑</a> : null,
        row.status === 'PLANNED' ? (
          <Popconfirm key="cancel" title="确定取消该拜访？" onConfirm={() => onCancel(row)}>
            <a style={{ color: '#ff4d4f' }}>取消</a>
          </Popconfirm>
        ) : null,
      ],
    },
  ]

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title="本月拜访计划" value={stats.totalPlanned} valueStyle={{ color: '#1677ff' }} />
          </div>
        </Col>
        <Col span={6}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title="已完成拜访" value={stats.totalDone} valueStyle={{ color: '#3f8600' }} />
          </div>
        </Col>
        <Col span={12}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '12px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)', height: '100%' }}>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              外勤拜访：计划 → 到达定位签到 → 小结自动写入客户跟进时间线（method=拜访）。
              重复签到会被拦截；过期签到自动标记"补签"。
            </Typography.Text>
          </div>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <ProTable<FieldVisit>
        size="small"
        headerTitle="拜访计划"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ pageSize: 10 }}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async (params) => {
          const res = await fetchVisits({ page: params.current ?? 1, pageSize: params.pageSize ?? 10 })
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建拜访
          </Button>,
        ]}
      />

      {/* 新建/编辑 */}
      <Modal
        title={editing ? '编辑拜访' : '新建拜访'}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={form} name="visitForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
            <Select showSearch optionFilterProp="label" placeholder="选择客户" options={customerOptions} />
          </Form.Item>
          <Form.Item name="theme" label="主题" rules={[{ required: true, message: '请输入拜访主题' }]}>
            <Input placeholder="如：谈续约、售后回访" />
          </Form.Item>
          <Form.Item name="visitTime" label="拜访时间" rules={[{ required: true, message: '请选择时间' }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="durationMinutes" label="预计时长(分)">
            <InputNumber min={10} step={10} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 签到 */}
      <Modal
        title={`拜访签到：${checkInVisitRow?.theme ?? ''}`}
        open={!!checkInVisitRow}
        onOk={() => void onCheckIn()}
        confirmLoading={saving}
        onCancel={() => setCheckInVisitRow(null)}
        okText="确认签到"
        destroyOnClose
      >
        <Form form={checkInForm} name="checkInForm" layout="vertical">
          <Form.Item name="locationText" label="位置">
            <Input placeholder="自动定位获取坐标，也可手动填写地址" />
          </Form.Item>
          <Form.Item name="summary" label="拜访小结">
            <Input.TextArea rows={3} placeholder="本次拜访内容、客户反馈…保存后写入客户跟进时间线" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
