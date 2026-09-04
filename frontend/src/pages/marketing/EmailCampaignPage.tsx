import { useEffect, useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import {
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
  const [form] = Form.useForm<FormValues>()
  const [testEmail, setTestEmail] = useState('')

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
      message.success(t('pages.marketing.emailCampaign.msgSent', { count: c.totalCount }))
      setModalOpen(false)
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
      await testSendCampaign(row.id, testEmail)
      message.success(t('pages.marketing.emailCampaign.msgTestSent'))
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
      render: (_, row) => <Tag color={STATUS_COLORS[row.status] ?? 'default'}>{row.status}</Tag>,
    },
    { title: t('pages.marketing.emailCampaign.colRecipients'), dataIndex: 'totalCount', width: 80, search: false },
    { title: t('pages.marketing.emailCampaign.colSuccess'), dataIndex: 'sentCount', width: 70, search: false, render: (_, row) => <Tag color="green">{row.sentCount}</Tag> },
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
        <a
          key="test"
          onClick={() => {
            setTestEmail('')
            void onTestSend(row)
          }}
        >
          {t('pages.marketing.emailCampaign.btnTest')}
        </a>,
      ],
    },
  ]

  return (
    <>
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
              render: (v: string) =>
                v === 'SENT' ? <Tag color="green">{t('pages.marketing.emailCampaign.statusSuccess')}</Tag> : <Tag color="red">{t('pages.marketing.emailCampaign.statusFailed')}</Tag>,
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
