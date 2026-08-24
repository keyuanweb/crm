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
      message.success(`已发送 ${c.totalCount} 封`)
      setModalOpen(false)
      reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '发送失败'))
    } finally {
      setSaving(false)
    }
  }

  const onTestSend = async (row: EmailCampaign) => {
    if (!testEmail) {
      message.warning('请先输入测试邮箱')
      return
    }
    try {
      await testSendCampaign(row.id, testEmail)
      message.success('测试邮件已发送')
    } catch (err) {
      message.error(extractErrorMessage(err, '发送失败'))
    }
  }

  const openDetail = async (row: EmailCampaign) => {
    setDetailDrawer(row)
    const res = await fetchCampaignDetail(row.id, { page: 1, pageSize: 50 })
    setDetailLogs(res.items)
  }

  const columns: ProColumns<EmailCampaign>[] = [
    { title: '活动名', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, row) => <Tag color={STATUS_COLORS[row.status] ?? 'default'}>{row.status}</Tag>,
    },
    { title: '收件人', dataIndex: 'totalCount', width: 80, search: false },
    { title: '成功', dataIndex: 'sentCount', width: 70, search: false, render: (_, row) => <Tag color="green">{row.sentCount}</Tag> },
    { title: '失败', dataIndex: 'failedCount', width: 70, search: false, render: (_, row) => <Tag color="red">{row.failedCount}</Tag> },
    { title: '打开', dataIndex: 'openCount', width: 70, search: false, render: (_, row) => <Tag color="blue">{row.openCount}</Tag> },
    { title: '点击', dataIndex: 'clickCount', width: 70, search: false, render: (_, row) => <Tag color="geekblue">{row.clickCount}</Tag> },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_, row) => [
        <a key="detail" onClick={() => void openDetail(row)}>
          记录
        </a>,
        <a
          key="test"
          onClick={() => {
            setTestEmail('')
            void onTestSend(row)
          }}
        >
          测试
        </a>,
      ],
    },
  ]

  return (
    <>
      <ProTable<EmailCampaign>
        size="small"
        headerTitle="邮件群发"
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
              placeholder="测试邮箱"
              value={testEmail}
              onChange={(e) => setTestEmail(e.target.value)}
              style={{ width: 180 }}
            />
          </Space>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建群发
          </Button>,
        ]}
      />

      <Modal
        title="新建邮件群发"
        open={modalOpen}
        onOk={() => void onSend()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="发送"
        destroyOnClose
      >
        <Form form={form} name="campaignForm" layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 1 }}>
          <Form.Item name="name" label="活动名" rules={[{ required: true, message: '请输入活动名' }]}>
            <Input placeholder="如：老客户召回" />
          </Form.Item>
          <Form.Item name="templateId" label="模板" rules={[{ required: true, message: '请选择模板' }]}>
            <Select placeholder="选择邮件模板" options={templateOptions} />
          </Form.Item>
          <Form.Item name="sourceType" label="收件人" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="SEGMENT">客户细分</Radio>
              <Radio value="CUSTOMER_IDS">全部客户（邮箱非空）</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.sourceType !== c.sourceType}>
            {({ getFieldValue }) =>
              getFieldValue('sourceType') === 'SEGMENT' ? (
                <Form.Item name="segmentId" label="细分" rules={[{ required: true, message: '请选择细分' }]}>
                  <Select
                    placeholder="选择客户细分"
                    options={segmentOptions.map((s) => ({ label: `${s.name}（${s.memberCount}）`, value: s.id }))}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`发送记录：${detailDrawer?.name ?? ''}`}
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
            { title: '邮箱', dataIndex: 'email' },
            { title: '主题', dataIndex: 'subject', ellipsis: true },
            {
              title: '状态',
              dataIndex: 'status',
              width: 80,
              render: (v: string) =>
                v === 'SENT' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
            },
            {
              title: '时间',
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
