import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { FieldVisit, VisitPayload } from '../../types/visit'

export default function VisitListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()

  const STATUS_META: Record<string, { text: string; color: string }> = {
    PLANNED: { text: t('pages.visit.statusPlanned'), color: 'processing' },
    DONE: { text: t('common.status.completed'), color: 'green' },
    CANCELED: { text: t('pages.visit.statusCanceled'), color: 'default' },
  }
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<FieldVisit | null>(null)
  const [checkInVisitRow, setCheckInVisitRow] = useState<FieldVisit | null>(null)
  const [checkInForm] = Form.useForm<{ summary?: string; locationText?: string }>()
  const [customerOptions, setCustomerOptions] = useState<{ label: string; value: number }[]>([])
  const [stats, setStats] = useState<{ totalPlanned: number; totalDone: number }>({ totalPlanned: 0, totalDone: 0 })
  const [form] = Form.useForm<VisitPayload>()
  // 签到与取消打的是 POST /field-visits/{id}/check-in 与 /cancel，两个端点同挂 visit:manage
  // （FieldVisitController）——所以只有一个判据。
  const can = usePerms([PERMS.visitManage])

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
        message.success(t('common.message.saved'))
      } else {
        await createVisit(payload)
        message.success(t('pages.visit.msgCreated'))
      }
      setModalOpen(false)
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.visit.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onCancel = async (row: FieldVisit) => {
    try {
      await cancelVisit(row.id)
      message.success(t('pages.visit.msgCanceled'))
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.visit.msgCancelFailed')))
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
      message.success(t('pages.visit.msgCheckInSuccess'))
      setCheckInVisitRow(null)
      reload()
      void loadStats()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.visit.msgCheckInFailed')))
    } finally {
      setSaving(false)
    }
  }

  const columns: ProColumns<FieldVisit>[] = [
    { title: t('pages.visit.colTheme'), dataIndex: 'theme' },
    {
      title: t('pages.visit.colCustomer'),
      dataIndex: 'customerName',
      width: 160,
      render: (_, row) => <a href={`#/customers/${row.customerId}`}>{row.customerName}</a>,
    },
    {
      title: t('pages.visit.colVisitTime'),
      dataIndex: 'visitTime',
      width: 150,
      render: (_, row) => (row.visitTime ? row.visitTime.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: t('pages.visit.colStatus'),
      dataIndex: 'status',
      width: 90,
      render: (_, row) => {
        const m = STATUS_META[row.status] ?? { text: row.status, color: 'default' }
        return <Tag color={m.color}>{m.text}</Tag>
      },
    },
    {
      title: t('pages.visit.checkIn'),
      search: false,
      width: 180,
      render: (_, row) =>
        row.checkInTime ? (
          <Space size={4} direction="vertical" style={{ fontSize: 12 }}>
            <span>{row.checkInTime.replace('T', ' ').slice(0, 16)}</span>
            {row.lateFlag ? <Tag color="orange" style={{ fontSize: 11 }}>{t('pages.visit.tagLateCheckIn')}</Tag> : null}
          </Space>
        ) : (
          <span style={{ color: '#bfbfbf' }}>-</span>
        ),
    },
    {
      title: t('pages.visit.colAction'),
      valueType: 'option',
      width: 170,
      render: (_, row) => [
        row.status === 'PLANNED' && can[PERMS.visitManage] ? (
          <a key="checkin" onClick={() => openCheckIn(row)}>
            <EnvironmentOutlined /> {t('pages.visit.checkIn')}
          </a>
        ) : null,
        row.status === 'PLANNED' ? <a key="edit" onClick={() => openEdit(row)}>{t('common.button.edit')}</a> : null,
        row.status === 'PLANNED' && can[PERMS.visitManage] ? (
          <Popconfirm key="cancel" title={t('pages.visit.confirmCancel')} onConfirm={() => onCancel(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('common.button.cancel')}</a>
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
            <Statistic title={t('pages.visit.statPlanned')} value={stats.totalPlanned} valueStyle={{ color: '#1677ff' }} />
          </div>
        </Col>
        <Col span={6}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '16px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}>
            <Statistic title={t('pages.visit.statDone')} value={stats.totalDone} valueStyle={{ color: '#3f8600' }} />
          </div>
        </Col>
        <Col span={12}>
          <div style={{ background: '#fff', borderRadius: 10, padding: '12px 20px', boxShadow: '0 1px 2px rgba(0,0,0,0.04)', height: '100%' }}>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              {t('pages.visit.hint')}
            </Typography.Text>
          </div>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <ProTable<FieldVisit>
        size="small"
        headerTitle={t('pages.visit.title')}
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
            {t('pages.visit.create')}
          </Button>,
        ]}
      />

      {/* 新建/编辑 */}
      <Modal
        title={editing ? t('pages.visit.edit') : t('pages.visit.create')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('common.button.save')}
        destroyOnClose
      >
        <Form form={form} name="visitForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="customerId" label={t('pages.visit.colCustomer')} rules={[{ required: true, message: t('pages.visit.msgCustomerRequired') }]}>
            <Select showSearch optionFilterProp="label" placeholder={t('pages.visit.placeholderCustomer')} options={customerOptions} />
          </Form.Item>
          <Form.Item name="theme" label={t('pages.visit.colTheme')} rules={[{ required: true, message: t('pages.visit.msgThemeRequired') }]}>
            <Input placeholder={t('pages.visit.placeholderTheme')} />
          </Form.Item>
          <Form.Item name="visitTime" label={t('pages.visit.formVisitTime')} rules={[{ required: true, message: t('pages.visit.msgTimeRequired') }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="durationMinutes" label={t('pages.visit.formDuration')}>
            <InputNumber min={10} step={10} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 签到 */}
      <Modal
        title={t('pages.visit.modalCheckInTitle', { theme: checkInVisitRow?.theme ?? '' })}
        open={!!checkInVisitRow}
        onOk={() => void onCheckIn()}
        confirmLoading={saving}
        onCancel={() => setCheckInVisitRow(null)}
        okText={t('pages.visit.btnConfirmCheckIn')}
        destroyOnClose
      >
        <Form form={checkInForm} name="checkInForm" layout="vertical">
          <Form.Item name="locationText" label={t('pages.visit.formLocation')}>
            <Input placeholder={t('pages.visit.placeholderLocation')} />
          </Form.Item>
          <Form.Item name="summary" label={t('pages.visit.formSummary')}>
            <Input.TextArea rows={3} placeholder={t('pages.visit.placeholderSummary')} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
