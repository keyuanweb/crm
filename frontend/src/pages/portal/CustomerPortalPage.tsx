import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Descriptions, Form, Input, InputNumber, List, Modal, Select, Space, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import {
  fetchPortalArticle,
  fetchPortalArticles,
  queryPortalTicket,
  submitPortalTicket,
} from '../../services/customerPortalService'
import { extractErrorMessage } from '../../services/apiClient'
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'

/** 客户自助门户（050，公开访问）：知识库浏览/在线提单/进度查询。 */
export default function CustomerPortalPage() {
  const { t } = useTranslation()
  const [keyword, setKeyword] = useState('')
  const [activeTab, setActiveTab] = useState('kb')

  const articles = useQuery({
    queryKey: ['portal-articles', keyword],
    queryFn: () => fetchPortalArticles(keyword || undefined, 1, 50),
  })

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Typography.Title level={3} style={{ textAlign: 'center' }}>
        {t('pages.portal.title')}
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
        {t('pages.portal.subtitle')}
      </Typography.Paragraph>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'kb',
            label: t('pages.portal.tabKb'),
            children: <KnowledgeBaseTab keyword={keyword} setKeyword={setKeyword} data={articles.data?.items} loading={articles.isLoading} />,
          },
          { key: 'submit', label: t('pages.portal.submitTicket'), children: <SubmitTicketTab /> },
          { key: 'track', label: t('pages.portal.tabTrack'), children: <TrackTicketTab /> },
        ]}
      />
    </div>
  )
}

function KnowledgeBaseTab({
  keyword,
  setKeyword,
  data,
  loading,
}: {
  keyword: string
  setKeyword: (v: string) => void
  data?: { id: number; title: string; category: string }[]
  loading: boolean
}) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [article, setArticle] = useState<{ id: number; title: string; content?: string; category?: string } | null>(null)

  return (
    <Card>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder={t('pages.portal.searchPlaceholder')}
          allowClear
          style={{ width: 320 }}
          onSearch={(v) => setKeyword(v)}
        />
        <Tag>{loading ? t('pages.portal.loading') : t('pages.portal.articleCount', { count: data?.length ?? 0 })}</Tag>
      </Space>
      <List
        loading={loading}
        dataSource={data ?? []}
        locale={{ emptyText: keyword ? t('pages.portal.emptyNoResult') : t('pages.portal.emptyNoArticles') }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <a
                key="view"
                onClick={() => {
                  void fetchPortalArticle(item.id)
                    .then((a) => setArticle(a))
                    .catch((e) => message.error(extractErrorMessage(e, t('pages.portal.msgLoadFailed'))))
                }}
              >
                {t('pages.portal.actionView')}
              </a>,
            ]}
          >
            <List.Item.Meta
              title={item.title}
              description={<Tag color="blue">{item.category}</Tag>}
            />
          </List.Item>
        )}
      />
      <Modal
        title={article?.title}
        open={!!article}
        onCancel={() => setArticle(null)}
        footer={null}
        width={640}
      >
        {article?.content ? (
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{article.content}</Typography.Paragraph>
        ) : null}
      </Modal>
    </Card>
  )
}

function SubmitTicketTab() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState<{ ticketId: number; status: string } | null>(null)

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const res = await submitPortalTicket({
        phone: values.phone,
        email: values.email,
        title: values.title.trim(),
        description: values.description,
        priority: values.priority ?? 'MEDIUM',
      })
      setResult(res)
      message.success(t('pages.portal.msgSubmitted'))
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.portal.msgSubmitFailed')))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      {result ? (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label={t('pages.portal.labelTicketId')}>{result.ticketId}</Descriptions.Item>
          <Descriptions.Item label={t('pages.portal.labelStatus')}>
            <Tag color="processing">{result.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.portal.labelNextStep')}>
            {t('pages.portal.nextStepHint')}
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Form form={form} layout="vertical" style={{ maxWidth: 520 }}>
          {/* 相邻成对的是 phone/email/title 三个；`description` 是多行文本（必须整行），
              它把 `priority` 隔在外面——两个整行项之间硬凑栅格会把字段排成破行。
              本表单容器只有 520px（上限 3 在此不生效，auto-fit 按 200 的下限排 2 列）。 */}
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH} maxCols={3}>
            <Form.Item name="phone" label={t('pages.portal.labelPhone')} rules={[{ required: true, message: t('pages.portal.msgPhoneRequired') }]}>
              <Input placeholder={t('pages.portal.placeholderPhone')} maxLength={20} />
            </Form.Item>
            <Form.Item name="email" label={t('pages.portal.labelEmail')}>
              <Input placeholder={t('pages.portal.placeholderOptional')} />
            </Form.Item>
            <Form.Item name="title" label={t('pages.portal.labelTitle')} rules={[{ required: true, message: t('pages.portal.msgTitleRequired') }]}>
              <Input maxLength={200} />
            </Form.Item>
          </FormGrid>
          <Form.Item name="description" label={t('pages.portal.labelDescription')}>
            <Input.TextArea rows={3} maxLength={2000} />
          </Form.Item>
          <Form.Item name="priority" label={t('pages.portal.labelPriority')} initialValue="MEDIUM">
            <Select
              options={[
                { value: 'LOW', label: t('pages.portal.priorityLow') },
                { value: 'MEDIUM', label: t('pages.portal.priorityMedium') },
                { value: 'HIGH', label: t('pages.portal.priorityHigh') },
                { value: 'URGENT', label: t('pages.portal.priorityUrgent') },
              ]}
            />
          </Form.Item>
          <Button type="primary" loading={saving} onClick={() => void submit()}>
            {t('pages.portal.submitTicket')}
          </Button>
        </Form>
      )}
    </Card>
  )
}

function TrackTicketTab() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [querying, setQuerying] = useState(false)
  const [status, setStatus] = useState<{
    ticketId: number
    status: string
    priority: string
    slaStatus?: string
    createdAt: string
    replies: { content: string; createdAt: string }[]
  } | null>(null)

  const query = async () => {
    const values = await form.validateFields()
    setQuerying(true)
    try {
      const res = await queryPortalTicket({
        ticketId: values.ticketId,
        phone: values.phone,
        email: values.email,
      })
      setStatus(res)
    } catch (err) {
      setStatus(null)
      message.error(extractErrorMessage(err, t('pages.portal.msgQueryFailed')))
    } finally {
      setQuerying(false)
    }
  }

  return (
    <Card>
      <Form form={form} layout="inline" style={{ marginBottom: 16 }}>
        <Form.Item name="ticketId" label={t('pages.portal.labelTicketId')} rules={[{ required: true, message: t('pages.portal.msgTicketIdRequired') }]}>
          <InputNumber min={1} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="phone" label={t('pages.portal.labelPhone')} rules={[{ required: true, message: t('pages.portal.msgPhoneEnterRequired') }]}>
          <Input placeholder={t('pages.portal.placeholderPhoneTrack')} maxLength={20} />
        </Form.Item>
        <Button type="primary" loading={querying} onClick={() => void query()}>
          {t('pages.portal.btnQuery')}
        </Button>
      </Form>
      {status && (
        <>
          <Descriptions column={3} bordered size="small">
            <Descriptions.Item label={t('pages.portal.labelTicketId')}>{status.ticketId}</Descriptions.Item>
            <Descriptions.Item label={t('pages.portal.labelStatus')}>
              <Tag color={status.status === 'CLOSED' ? 'success' : 'processing'}>{status.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="SLA">{status.slaStatus ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('pages.portal.labelPriority')}>{status.priority}</Descriptions.Item>
            <Descriptions.Item label={t('pages.portal.labelSubmittedAt')} span={2}>
              {status.createdAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            {t('pages.portal.titleReplies')}
          </Typography.Title>
          <List
            dataSource={status.replies}
            locale={{ emptyText: t('pages.portal.emptyNoReplies') }}
            renderItem={(r) => (
              <List.Item>
                <List.Item.Meta
                  title={r.createdAt.replace('T', ' ').slice(0, 19)}
                  description={r.content}
                />
              </List.Item>
            )}
          />
        </>
      )}
    </Card>
  )
}
