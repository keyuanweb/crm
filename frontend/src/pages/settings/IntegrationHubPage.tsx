import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Drawer, Form, Input, Modal, Popconfirm, Select, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createChannel,
  deleteChannel,
  fetchChannelDeliveries,
  fetchChannels,
  toggleChannel,
  updateChannel,
} from '../../services/integrationService'
import { extractErrorMessage } from '../../services/apiClient'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { type IntegrationChannel } from '../../types/integration'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

interface FormValues {
  channelType: string
  name: string
  webhookUrl: string
  enabled: boolean
}

/** 集成中心页（058，仅 ADMIN）：通道管理 + 推送记录。 */
export default function IntegrationHubPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<IntegrationChannel | null>(null)
  const [deliveryChannel, setDeliveryChannel] = useState<IntegrationChannel | null>(null)
  const [form] = Form.useForm<FormValues>()
  // 启停与删除通道分别走 POST /integration-channels/{id}/toggle 与 DELETE /integration-channels/{id}，
  // IntegrationChannelController 上两个方法挂的是同一个 integration:manage。
  const can = usePerms([PERMS.integrationManage])

  const columns: ProColumns<IntegrationChannel>[] = [
    {
      title: t('pages.integrationHub.colType'),
      dataIndex: 'channelType',
      valueEnum: Object.fromEntries(Object.keys(ENUM_KEYS.channelType).map((code) => [code, { text: labelOf(t, ENUM_KEYS.channelType, code) }])),
      render: (_, row) => <Tag color="blue">{labelOf(t, ENUM_KEYS.channelType, row.channelType)}</Tag>,
    },
    { title: t('pages.integrationHub.colName'), dataIndex: 'name' },
    { title: t('pages.integrationHub.colWebhookUrl'), dataIndex: 'webhookUrl', search: false, ellipsis: true },
    {
      title: t('pages.integrationHub.colStatus'),
      dataIndex: 'enabled',
      search: false,
      render: (_, row) => (row.enabled ? <Tag color="green">{t('pages.integrationHub.enabled')}</Tag> : <Tag>{t('pages.integrationHub.disabled')}</Tag>),
    },
    {
      title: t('pages.integrationHub.colAction'),
      valueType: 'option',
      render: (_, row) => [
        <a key="deliveries" onClick={() => setDeliveryChannel(row)}>
          {t('pages.integrationHub.deliveryRecords')}
        </a>,
        can[PERMS.integrationManage] && (
          <a key="toggle" onClick={() => void onToggle(row)}>
            {row.enabled ? t('pages.integrationHub.disable') : t('pages.integrationHub.enable')}
          </a>
        ),
        <a key="edit" onClick={() => openEdit(row)}>
          {t('pages.integrationHub.edit')}
        </a>,
        can[PERMS.integrationManage] && (
          <Popconfirm key="del" title={t('pages.integrationHub.confirmDelete')} onConfirm={() => void onDelete(row)}>
            <a style={{ color: '#ff4d4f' }}>{t('pages.integrationHub.delete')}</a>
          </Popconfirm>
        ),
      ],
    },
  ]

  const openEdit = (row: IntegrationChannel) => {
    setEditing(row)
    form.setFieldsValue({ channelType: row.channelType, name: row.name, webhookUrl: row.webhookUrl, enabled: row.enabled })
    setModalOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await updateChannel(editing.id, values)
        message.success(t('pages.integrationHub.msgSaved'))
      } else {
        await createChannel(values)
        message.success(t('pages.integrationHub.msgCreated'))
      }
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.integrationHub.msgSaveFailed')))
    }
  }

  const onToggle = async (row: IntegrationChannel) => {
    try {
      await toggleChannel(row.id)
      message.success(row.enabled ? t('pages.integrationHub.msgDisabled') : t('pages.integrationHub.msgEnabled'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.integrationHub.msgOperationFailed')))
    }
  }

  const onDelete = async (row: IntegrationChannel) => {
    try {
      await deleteChannel(row.id)
      message.success(t('pages.integrationHub.msgDeleted'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.integrationHub.msgDeleteFailed')))
    }
  }

  return (
    <>
      <ProTable<IntegrationChannel>
        headerTitle={t('pages.integrationHub.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={false}
        request={async () => {
          const items = await fetchChannels()
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              form.resetFields()
              form.setFieldsValue({ enabled: true })
              setModalOpen(true)
            }}
          >
            {t('pages.integrationHub.btnAdd')}
          </Button>,
        ]}
      />
      <Modal
        title={editing ? t('pages.integrationHub.modalEditTitle') : t('pages.integrationHub.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.integrationHub.btnSave')}
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="channelType" label={t('pages.integrationHub.formChannelTypeLabel')} rules={[{ required: true, message: t('pages.integrationHub.formChannelTypeRequired') }]}>
              <Select options={Object.keys(ENUM_KEYS.channelType).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.channelType, code) }))} />
            </Form.Item>
            <Form.Item name="name" label={t('pages.integrationHub.formNameLabel')} rules={[{ required: true, message: t('pages.integrationHub.formNameRequired') }]}>
              <Input placeholder={t('pages.integrationHub.formNamePlaceholder')} maxLength={100} />
            </Form.Item>
            <Form.Item name="webhookUrl" label={t('pages.integrationHub.formWebhookUrlLabel')} rules={[{ required: true, message: t('pages.integrationHub.formWebhookUrlRequired') }]}>
              <Input placeholder={t('pages.integrationHub.formWebhookUrlPlaceholder')} />
            </Form.Item>
            <Form.Item name="enabled" label={t('pages.integrationHub.formEnabledLabel')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </FormGrid>
        </Form>
      </Modal>
      <Drawer
        title={`${t('pages.integrationHub.deliveryRecords')} · ${deliveryChannel?.name ?? ''}`}
        open={!!deliveryChannel}
        onClose={() => setDeliveryChannel(null)}
        width={520}
      >
        {deliveryChannel && <DeliveryList channelId={deliveryChannel.id} />}
      </Drawer>
    </>
  )
}

function DeliveryList({ channelId }: { channelId: number }) {
  const { t } = useTranslation()
  const [records, setRecords] = useState<{ id: number; eventType: string; status: string; httpStatus?: number; error?: string; createdAt: string }[]>([])
  useEffect(() => {
    void fetchChannelDeliveries(channelId, 1, 50)
      .then((res) => setRecords(res.items))
      .catch(() => undefined)
  }, [channelId])

  return (
    <div>
      {records.length === 0 ? (
        <span style={{ color: '#8c8c8c' }}>{t('pages.integrationHub.noRecords')}</span>
      ) : (
        records.map((r) => (
          <div key={r.id} style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, marginBottom: 8 }}>
            <Tag color="blue">{r.eventType}</Tag>
            {/* 085（FR-V13）：三态。原先是二元（非 SUCCESS 一律渲染为红色"失败"），于是后端新增的
                PENDING 会被显示成"失败"——把"记录不反映真实"原样搬到界面上。取色沿用 ExportCenterPage
                对非终态用 'processing' 的先例。 */}
            {r.status === 'SUCCESS' ? (
              <Tag color="green">{t('pages.integrationHub.success')}</Tag>
            ) : r.status === 'PENDING' ? (
              <Tag color="processing">{t('pages.integrationHub.pending')}</Tag>
            ) : (
              <Tag color="red">{t('pages.integrationHub.failed')}</Tag>
            )}
            <span style={{ color: '#8c8c8c', marginLeft: 8 }}>{r.createdAt.replace('T', ' ').slice(0, 19)}</span>
            {r.error && <div style={{ color: '#cf1322', marginTop: 4 }}>{r.error}</div>}
          </div>
        ))
      )}
    </div>
  )
}
