import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, InputNumber, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createSlaPolicy,
  deleteSlaPolicy,
  fetchSlaOverview,
  fetchSlaPolicies,
  updateSlaPolicy,
  type SlaPolicyPayload,
} from '../../services/slaService'
import { extractErrorMessage } from '../../services/apiClient'
import { type TicketPriority } from '../../types/ticket'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { SlaPolicy } from '../../types/sla'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

interface FormValues {
  priority: TicketPriority
  respondHours?: number
  resolveHours?: number
  enabled: boolean
}

export default function SlaPolicyListPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<SlaPolicy | null>(null)
  const [overview, setOverview] = useState<{ totalOpen: number; overdue: number } | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 删除策略走 DELETE /sla-policies/{id}，SlaPolicyController 上标的是 sla:manage。
  const can = usePerms([PERMS.slaManage])

  const reload = () => actionRef.current?.reload()

  const loadOverview = async () => {
    try {
      const o = await fetchSlaOverview()
      setOverview(o)
    } catch {
      // 忽略统计加载失败
    }
  }

  useEffect(() => {
    void loadOverview()
  }, [])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ enabled: true })
    setModalOpen(true)
  }

  const openEdit = (row: SlaPolicy) => {
    setEditing(row)
    form.setFieldsValue({
      priority: row.priority as TicketPriority,
      respondHours: row.respondHours,
      resolveHours: row.resolveHours,
      enabled: row.enabled === 1,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: SlaPolicyPayload = {
      priority: values.priority,
      respondHours: values.respondHours,
      resolveHours: values.resolveHours,
      enabled: values.enabled ? 1 : 0,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateSlaPolicy(editing.id, { ...payload, version: editing.version })
        message.success(t('pages.slaPolicy.msgSaved'))
      } else {
        await createSlaPolicy(payload)
        message.success(t('pages.slaPolicy.msgCreated'))
      }
      setModalOpen(false)
      reload()
      void loadOverview()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.slaPolicy.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: SlaPolicy) => {
    try {
      await deleteSlaPolicy(row.id)
      message.success(t('pages.slaPolicy.msgDeleted'))
      reload()
      void loadOverview()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.slaPolicy.msgDeleteFailed')))
    }
  }

  const columns: ProColumns<SlaPolicy>[] = [
    {
      title: t('pages.slaPolicy.colPriority'),
      dataIndex: 'priority',
      render: (_, row) => (
        <Tag color="blue">{labelOf(t, ENUM_KEYS.ticketPriority, row.priority)}</Tag>
      ),
    },
    {
      title: t('pages.slaPolicy.colRespondHours'),
      dataIndex: 'respondHours',
      search: false,
      render: (_, row) => row.respondHours ?? t('pages.slaPolicy.unconstrained'),
    },
    {
      title: t('pages.slaPolicy.colResolveHours'),
      dataIndex: 'resolveHours',
      search: false,
      render: (_, row) => row.resolveHours ?? t('pages.slaPolicy.unconstrained'),
    },
    {
      title: t('pages.slaPolicy.colEnabled'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) =>
        row.enabled === 1 ? <Tag color="green">{t('pages.slaPolicy.enabled')}</Tag> : <Tag>{t('pages.slaPolicy.disabled')}</Tag>,
    },
    {
      title: t('pages.slaPolicy.colAction'),
      valueType: 'option',
      width: 140,
      render: (_, row) => [
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.slaPolicy.edit')}
        </a>,
        can[PERMS.slaManage] && (
          <Popconfirm
            key="delete"
            title={t('pages.slaPolicy.confirmDelete', { priority: labelOf(t, ENUM_KEYS.ticketPriority, row.priority) })}
            onConfirm={() => onDelete(row)}
          >
            <a style={{ color: '#ff4d4f' }}>{t('pages.slaPolicy.delete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Tag color={overview && overview.overdue > 0 ? 'red' : 'green'} style={{ fontSize: 13, padding: '4px 10px' }}>
          {t('pages.slaPolicy.overviewText', { totalOpen: overview?.totalOpen ?? 0, overdue: overview?.overdue ?? 0 })}
        </Tag>
      </div>
      <div style={{ height: 16 }} />

      <ProTable<SlaPolicy>
        size="small"
        headerTitle={t('pages.slaPolicy.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 10 }}
        request={async (params) => {
          const res = await fetchSlaPolicies(params.current ?? 1, params.pageSize ?? 10)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.slaPolicy.btnAdd')}
          </Button>,
        ]}
      />

      <Modal
        title={editing ? t('pages.slaPolicy.modalEditTitle') : t('pages.slaPolicy.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.slaPolicy.btnSave')}
        destroyOnClose
        width={520}
      >
        <Form form={form} name="slaForm" layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item
              name="priority"
              label={t('pages.slaPolicy.formPriorityLabel')}
              rules={[{ required: true, message: t('pages.slaPolicy.formPriorityRequired') }]}
            >
              <Select
                disabled={!!editing}
                options={Object.keys(ENUM_KEYS.ticketPriority).map((code) => ({
                  value: code,
                  label: labelOf(t, ENUM_KEYS.ticketPriority, code),
                }))}
              />
            </Form.Item>
            <Form.Item name="respondHours" label={t('pages.slaPolicy.formRespondHoursLabel')}>
              <InputNumber min={1} style={{ width: '100%' }} placeholder={t('pages.slaPolicy.formRespondHoursPlaceholder')} />
            </Form.Item>
            <Form.Item name="resolveHours" label={t('pages.slaPolicy.formResolveHoursLabel')}>
              <InputNumber min={1} style={{ width: '100%' }} placeholder={t('pages.slaPolicy.formResolveHoursPlaceholder')} />
            </Form.Item>
            <Form.Item name="enabled" label={t('pages.slaPolicy.formEnabledLabel')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
    </>
  )
}
