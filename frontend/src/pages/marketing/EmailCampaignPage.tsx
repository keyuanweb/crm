import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
  Alert,
  App,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  createEmailCampaign,
  fetchCampaignDetail,
  fetchEmailCampaigns,
  fetchEmailTemplates,
  testSendCampaign,
} from '../../services/emailService'
import { fetchSegments } from '../../services/segmentService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { EmailCampaign, EmailSendLog } from '../../types/email'
import type { Segment } from '../../types/tag'

interface FormValues {
  name: string
  templateId: number
  sourceType: 'SEGMENT' | 'CUSTOMER_IDS'
  segmentId?: number
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  DONE: 'green',
  FAILED: 'red',
  // SMTP 未配置 → 整批从未发出（一期诚信修复）。用 warning 色而非绿色，避免与 DONE 混淆。
  SKIPPED: 'orange',
}

/** 批次状态文案键（后端返回英文枚举，直接渲染会在中文界面露出 SKIPPED 之类的字面量）。 */
const CAMPAIGN_STATUS_KEYS: Record<string, string> = {
  PENDING: 'campaignStatusPending',
  RUNNING: 'campaignStatusRunning',
  DONE: 'campaignStatusDone',
  FAILED: 'campaignStatusFailed',
  SKIPPED: 'campaignStatusSkipped',
}

/** 单封发送状态：四态，不能把 SKIPPED/PENDING 一律画成红色"失败"。 */
const LOG_STATUS_KEYS: Record<string, { color: string; key: string }> = {
  SENT: { color: 'green', key: 'statusSuccess' },
  FAILED: { color: 'red', key: 'statusFailed' },
  SKIPPED: { color: 'orange', key: 'statusSkipped' },
  PENDING: { color: 'default', key: 'statusPending' },
}

export default function EmailCampaignPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [templateOptions, setTemplateOptions] = useState<{ label: string; value: number }[]>([])
  const [segmentOptions, setSegmentOptions] = useState<Segment[]>([])
  const [detailDrawer, setDetailDrawer] = useState<EmailCampaign | null>(null)
  const [detailLogs, setDetailLogs] = useState<EmailSendLog[]>([])
  // 列表中只要有批次是 SKIPPED，就说明"这批邮件从未发出"——顶部给出警告条
  const [hasSkippedBatch, setHasSkippedBatch] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const [testEmail, setTestEmail] = useState('')
  // 086：测试发送按权限码收口（POST /email-campaigns/{id}/test 挂 email:manage）。
  const can = usePerms([PERMS.emailManage])

  useEffect(() => {
    void fetchEmailTemplates().then((ts) =>
      setTemplateOptions(ts.map((t) => ({ label: t.name, value: t.id }))),
    )
    void fetchSegments().then(setSegmentOptions)
  }, [])

  const reload = () => actionRef.current?.reload()

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ sourceType: 'SEGMENT' })
    setModalOpen(true)
  }

  const onSend = async () => {
    const values = await form.validateFields()
    const payload: Record<string, unknown> = {
      name: values.name.trim(),
      templateId: values.templateId,
      sourceType: values.sourceType,
    }
    if (values.sourceType === 'SEGMENT') {
      payload.segmentId = values.segmentId
    }
    setSaving(true)
    try {
      const c = await createEmailCampaign(payload as never)
      setModalOpen(false)
      // 未配置 SMTP 时后端把批次标为 SKIPPED —— 此时提示"已发送 N 封"就是假成功。
      // 配置了 SMTP 走异步发送，此刻结果未知，所以只说"已提交"，不宣称已发出。
      if (c.status === 'SKIPPED') {
        message.warning(t('pages.marketing.emailCampaign.warningSkipped'))
      } else if (c.totalCount === 0) {
        // 收件人为 0 时接口仍返回成功：不提示的话，用户会以为发出去了（实际一封没发）
        message.warning(t('pages.marketing.emailCampaign.msgNoRecipients'))
      } else {
        message.success(t('pages.marketing.emailCampaign.msgQueued', { count: c.totalCount }))
      }
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.emailCampaign.msgSendFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onTestSend = async (row: EmailCampaign) => {
    if (!testEmail) {
      message.warning(t('pages.marketing.emailCampaign.msgEnterTestEmail'))
      return
    }
    try {
      const sent = await testSendCampaign(row.id, testEmail)
      if (sent) {
        message.success(t('pages.marketing.emailCampaign.msgTestSent'))
      } else {
        message.warning(t('pages.marketing.emailCampaign.msgTestNotSent'))
      }
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.marketing.emailCampaign.msgSendFailed')))
    }
  }

  const openDetail = async (row: EmailCampaign) => {
    setDetailDrawer(row)
    const res = await fetchCampaignDetail(row.id, { page: 1, pageSize: 50 })
    setDetailLogs(res.items)
  }

  const columns: ProColumns<EmailCampaign>[] = [
    { title: t('pages.marketing.emailCampaign.colName'), dataIndex: 'name' },
    {
      title: t('pages.marketing.emailCampaign.colStatus'),
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) => {
        const key = CAMPAIGN_STATUS_KEYS[row.status]
        return (
          <Tag color={STATUS_COLORS[row.status] ?? 'default'}>
            {key ? t(`pages.marketing.emailCampaign.${key}`) : row.status}
          </Tag>
        )
      },
    },
    { title: t('pages.marketing.emailCampaign.colRecipients'), dataIndex: 'totalCount', width: 80, search: false },
    // 0 封不要画成绿色：绿色数字会被读成"发出去了"，而 SKIPPED 批次的成功数正是 0
    { title: t('pages.marketing.emailCampaign.colSuccess'), dataIndex: 'sentCount', width: 70, search: false, render: (_, row) => <Tag color={row.sentCount > 0 ? 'green' : 'default'}>{row.sentCount}</Tag> },
    { title: t('pages.marketing.emailCampaign.colFailed'), dataIndex: 'failedCount', width: 70, search: false, render: (_, row) => <Tag color="red">{row.failedCount}</Tag> },
    { title: t('pages.marketing.emailCampaign.colOpened'), dataIndex: 'openCount', width: 70, search: false, render: (_, row) => <Tag color="blue">{row.openCount}</Tag> },
    { title: t('pages.marketing.emailCampaign.colClicked'), dataIndex: 'clickCount', width: 70, search: false, render: (_, row) => <Tag color="geekblue">{row.clickCount}</Tag> },
    {
      title: t('pages.marketing.emailCampaign.colAction'),
      valueType: 'option',
      width: 150,
      render: (_, row) => [
        <a key="detail" onClick={() => void openDetail(row)}>
          {t('pages.marketing.emailCampaign.btnRecords')}
        </a>,
        can[PERMS.emailManage] ? (
          <a
            key="test"
            onClick={() => {
              setTestEmail('')
              void onTestSend(row)
            }}
          >
            {t('pages.marketing.emailCampaign.btnTest')}
          </a>
        ) : null,
      ],
    },
  ]

  return (
    <>
      {hasSkippedBatch ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('pages.marketing.emailCampaign.warningSkipped')}
          description={t('pages.marketing.emailCampaign.warningSkippedDesc')}
        />
      ) : null}
      <ProTable<EmailCampaign>
        size="small"
        headerTitle={t('pages.marketing.emailCampaign.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={false}
        cardProps={{ style: { borderRadius: 10 } }}
        request={async () => {
          const items = await fetchEmailCampaigns()
          setHasSkippedBatch(items.some((c) => c.status === 'SKIPPED'))
          return { data: items, success: true, total: items.length }
        }}
        toolBarRender={() => [
          <Space key="test-box">
            <Input
              placeholder={t('pages.marketing.emailCampaign.phTestEmail')}
              value={testEmail}
              onChange={(e) => setTestEmail(e.target.value)}
              style={{ width: 180 }}
            />
          </Space>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.marketing.emailCampaign.btnCreate')}
          </Button>,
        ]}
      />

      <Modal
        title={t('pages.marketing.emailCampaign.modalCreateTitle')}
        open={modalOpen}
        onOk={() => void onSend()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.marketing.emailCampaign.btnSend')}
        destroyOnClose
      >
        <Form form={form} name="campaignForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="name" label={t('pages.marketing.emailCampaign.formName')} rules={[{ required: true, message: t('pages.marketing.emailCampaign.msgNameRequired') }]}>
            <Input placeholder={t('pages.marketing.emailCampaign.formNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="templateId" label={t('pages.marketing.emailCampaign.formTemplate')} rules={[{ required: true, message: t('pages.marketing.emailCampaign.msgTemplateRequired') }]}>
            <Select placeholder={t('pages.marketing.emailCampaign.formTemplatePlaceholder')} options={templateOptions} />
          </Form.Item>
          <Form.Item name="sourceType" label={t('pages.marketing.emailCampaign.formSource')} rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="SEGMENT">{t('pages.marketing.emailCampaign.sourceSegment')}</Radio>
              <Radio value="CUSTOMER_IDS">{t('pages.marketing.emailCampaign.sourceAllCustomers')}</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.sourceType !== c.sourceType}>
            {({ getFieldValue }) =>
              getFieldValue('sourceType') === 'SEGMENT' ? (
                <Form.Item name="segmentId" label={t('pages.marketing.emailCampaign.formSegment')} rules={[{ required: true, message: t('pages.marketing.emailCampaign.msgSegmentRequired') }]}>
                  <Select
                    placeholder={t('pages.marketing.emailCampaign.formSegmentPlaceholder')}
                    options={segmentOptions.map((s) => ({ label: `${s.name}（${s.memberCount}）`, value: s.id }))}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={t('pages.marketing.emailCampaign.drawerTitle', { name: detailDrawer?.name ?? '' })}
        open={!!detailDrawer}
        onClose={() => setDetailDrawer(null)}
        width={560}
      >
        {detailLogs.some((l) => l.status === 'SKIPPED') ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('pages.marketing.emailCampaign.warningSkipped')}
            description={detailLogs.find((l) => l.status === 'SKIPPED')?.errorMessage}
          />
        ) : null}
        <Table<EmailSendLog>
          size="small"
          rowKey="id"
          dataSource={detailLogs}
          pagination={false}
          columns={[
            { title: t('pages.marketing.emailCampaign.colEmail'), dataIndex: 'email' },
            { title: t('pages.marketing.emailCampaign.colSubject'), dataIndex: 'subject', ellipsis: true },
            {
              title: t('pages.marketing.emailCampaign.colStatus'),
              dataIndex: 'status',
              width: 80,
              // 悬停显示 errorMessage：SKIPPED 的原因是"SMTP 未配置"，FAILED 是具体异常
              render: (v: string, row) => {
                const meta = LOG_STATUS_KEYS[v]
                return (
                  <Tag color={meta?.color ?? 'default'} title={row.errorMessage}>
                    {meta ? t(`pages.marketing.emailCampaign.${meta.key}`) : v}
                  </Tag>
                )
              },
            },
            {
              title: t('pages.marketing.emailCampaign.colTime'),
              dataIndex: 'createdAt',
              width: 140,
              render: (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '-'),
            },
          ]}
        />
      </Drawer>
    </>
  )
}
