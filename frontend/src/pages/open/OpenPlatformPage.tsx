import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Form, Input, Modal, Popconfirm, Select, Space, Tabs, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createApiKey,
  createWebhook,
  deleteWebhook,
  fetchApiKeys,
  fetchWebhooks,
  revokeApiKey,
  toggleWebhook,
} from '../../services/openPlatformService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import { type ApiKey, type WebhookSubscription } from '../../types/openPlatform'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'

/**
 * 086 权限判据：本页的**全部**端点（含两个列表读端点）挂的都是 `open_platform:manage`
 * （`OpenPlatformController.java:70,77,86,97,104,111,118`），故：
 *
 * <ul>
 *   <li>「吊销 API Key」「启停 Webhook」「删除 Webhook」三个破坏性动作收口到该码；
 *   <li>**但要如实说明：这三道判据当下是冗余的**——列表本身（`:77` / `:104`）挂的也是同一个码，
 *       "能看见这张表"已蕴含"持有该码"，所以判据恒真、不会真的隐藏按钮。仍挂的理由同
 *       `DepartmentListPage`：码与端点逐字对应，且后端若把读与写拆码，这里立刻变成有效收窄。
 *   <li>「新建 API Key」「新建 Webhook」按 086 决策 1（新建不收口）**刻意不挂**。
 * </ul>
 *
 * <p>注意本页 JSDoc 自称"仅 ADMIN"：`open_platform:manage` 的**非 ADMIN 持有者为零**，
 * 故对非管理员的效果与"仅 ADMIN"一致，但表达成了可授予的码。
 */
const OPEN_PLATFORM_PERMS = [PERMS.openPlatformManage] as const

/** 开放平台页（055，仅 ADMIN）：API Key + Webhook。 */
export default function OpenPlatformPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState('keys')
  return (
    <Tabs
      activeKey={tab}
      onChange={setTab}
      items={[
        { key: 'keys', label: t('pages.openPlatform.tabApiKeys'), children: <ApiKeyTab /> },
        { key: 'webhooks', label: t('pages.openPlatform.tabWebhooks'), children: <WebhookTab /> },
      ]}
    />
  )
}

function ApiKeyTab() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [created, setCreated] = useState<ApiKey | null>(null)
  const [form] = Form.useForm()
  const can = usePerms(OPEN_PLATFORM_PERMS)

  const columns: ProColumns<ApiKey>[] = [
    { title: t('pages.openPlatform.colName'), dataIndex: 'name' },
    {
      title: t('pages.openPlatform.colKeyPrefix'),
      dataIndex: 'keyPrefix',
      render: (_, row) => <Tag>{row.keyPrefix}</Tag>,
    },
    { title: t('pages.openPlatform.colScopes'), dataIndex: 'scopes', render: (_, row) => (row.scopes ?? []).join(', ') || '-' },
    {
      title: t('pages.openPlatform.colStatus'),
      dataIndex: 'status',
      render: (_, row) => (row.status === 'ACTIVE' ? <Tag color="green">{t('pages.openPlatform.enabled')}</Tag> : <Tag color="red">{t('pages.openPlatform.revoked')}</Tag>),
    },
    {
      title: t('pages.openPlatform.colLastUsed'),
      dataIndex: 'lastUsedAt',
      search: false,
      render: (_, row) => (row.lastUsedAt ? row.lastUsedAt.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: t('pages.openPlatform.colAction'),
      valueType: 'option',
      // 权限判据嵌在状态判据**之内**：已吊销的行照旧显示灰色状态文案（那是状态指示不是动作），
      // 只有 ACTIVE 行才轮到"有没有权限吊销我"这个问题。两个判据是 ∧，不互相替代。
      render: (_, row) =>
        row.status === 'ACTIVE' ? (
          can[PERMS.openPlatformManage] ? (
            <Popconfirm
              key="revoke"
              title={t('pages.openPlatform.confirmRevoke', { name: row.name })}
              onConfirm={() => void onRevoke(row)}
            >
              <a style={{ color: '#ff4d4f' }}>{t('pages.openPlatform.revoke')}</a>
            </Popconfirm>
          ) : null
        ) : (
          <span key="revoked" style={{ color: '#bbb' }}>{t('pages.openPlatform.revoked')}</span>
        ),
    },
  ]

  const onRevoke = async (row: ApiKey) => {
    try {
      await revokeApiKey(row.id)
      message.success(t('pages.openPlatform.msgRevoked'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.openPlatform.msgRevokeFailed')))
    }
  }

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      const key = await createApiKey({
        name: values.name.trim(),
        scopes: values.scopes ?? [],
      })
      setCreated(key)
      setModalOpen(false)
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.openPlatform.msgCreateFailed')))
    }
  }

  return (
    <>
      <ProTable<ApiKey>
        headerTitle={t('pages.openPlatform.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchApiKeys(params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>
            {t('pages.openPlatform.btnAdd')}
          </Button>,
        ]}
      />
      <Modal title={t('pages.openPlatform.modalCreateTitle')} open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText={t('pages.openPlatform.create')} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('pages.openPlatform.formNameLabel')} rules={[{ required: true, message: t('pages.openPlatform.formNameRequired') }]}>
            <Input placeholder={t('pages.openPlatform.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="scopes" label={t('pages.openPlatform.formScopesLabel')} initialValue={['customer:read']}>
            <Select
              mode="multiple"
              options={[
                { value: 'customer:read', label: t('pages.openPlatform.scopeCustomerRead') },
                { value: 'lead:read', label: t('pages.openPlatform.scopeLeadRead') },
                { value: 'lead:write', label: t('pages.openPlatform.scopeLeadWrite') },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={t('pages.openPlatform.modalCreatedTitle')}
        open={!!created}
        onCancel={() => setCreated(null)}
        footer={
          <Button type="primary" onClick={() => setCreated(null)}>
            {t('pages.openPlatform.saved')}
          </Button>
        }
      >
        <Typography.Paragraph type="danger">
          {t('pages.openPlatform.warningSaveKey')}
        </Typography.Paragraph>
        <Input value={created?.key} readOnly />
      </Modal>
    </>
  )
}

function WebhookTab() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [webhooks, setWebhooks] = useState<WebhookSubscription[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [createdSecret, setCreatedSecret] = useState<WebhookSubscription | null>(null)
  const [form] = Form.useForm()
  const can = usePerms(OPEN_PLATFORM_PERMS)

  const reload = () => {
    void fetchWebhooks().then(setWebhooks).catch(() => undefined)
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onCreate = async () => {
    const values = await form.validateFields()
    try {
      const sub = await createWebhook({ eventType: values.eventType, callbackUrl: values.callbackUrl.trim() })
      setCreatedSecret(sub)
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.openPlatform.msgCreateFailed')))
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true) }}>
        {t('pages.openPlatform.btnAddWebhook')}
      </Button>
      {webhooks.map((w) => (
        <div key={w.id} style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: '12px 16px' }}>
          <Space size={16}>
            <Tag color="blue">{labelOf(t, ENUM_KEYS.webhookEvent, w.eventType)}</Tag>
            <Typography.Text code>{w.callbackUrl}</Typography.Text>
            {w.enabled ? <Tag color="green">{t('pages.openPlatform.enabled')}</Tag> : <Tag>{t('pages.openPlatform.disabled')}</Tag>}
            {can[PERMS.openPlatformManage] && (
              <Button size="small" onClick={() => void toggle(w.id)}>
                {w.enabled ? t('pages.openPlatform.disable') : t('pages.openPlatform.enable')}
              </Button>
            )}
            {can[PERMS.openPlatformManage] && (
              <Popconfirm title={t('pages.openPlatform.confirmDelete')} onConfirm={() => void remove(w.id)}>
                <Button size="small" danger>
                  {t('pages.openPlatform.delete')}
                </Button>
              </Popconfirm>
            )}
          </Space>
        </div>
      ))}
      <Modal title={t('pages.openPlatform.modalCreateWebhookTitle')} open={modalOpen} onOk={() => void onCreate()} onCancel={() => setModalOpen(false)} okText={t('pages.openPlatform.create')} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="eventType" label={t('pages.openPlatform.formEventTypeLabel')} rules={[{ required: true, message: t('pages.openPlatform.formEventTypeRequired') }]}>
            <Select options={Object.keys(ENUM_KEYS.webhookEvent).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.webhookEvent, code) }))} />
          </Form.Item>
          <Form.Item name="callbackUrl" label={t('pages.openPlatform.formCallbackUrlLabel')} rules={[{ required: true, message: t('pages.openPlatform.formCallbackUrlRequired') }]}>
            <Input placeholder={t('pages.openPlatform.formCallbackUrlPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={t('pages.openPlatform.modalCreatedWebhookTitle')}
        open={!!createdSecret}
        onCancel={() => setCreatedSecret(null)}
        footer={<Button type="primary" onClick={() => setCreatedSecret(null)}>{t('pages.openPlatform.saved')}</Button>}
      >
        <Typography.Paragraph type="danger">{t('pages.openPlatform.webhookSecretWarning')}</Typography.Paragraph>
        <Input value={createdSecret?.secret} readOnly />
      </Modal>
    </Space>
  )

  async function toggle(id: number) {
    try {
      await toggleWebhook(id)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.openPlatform.msgOperationFailed')))
    }
  }

  async function remove(id: number) {
    try {
      await deleteWebhook(id)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.openPlatform.msgDeleteFailed')))
    }
  }
}
